package com.ntth.spring_boot_heroku_cinema_app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntth.spring_boot_heroku_cinema_app.Config.ZpConfig;
import com.ntth.spring_boot_heroku_cinema_app.filter.ZpSigner;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Ticket;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ZaloPayService {
    private final ZpConfig cfg;
    private final ZpSigner signer;
    private final RestTemplate rest;

    public ZaloPayService(ZpConfig cfg, ZpSigner signer) {
        this.cfg = cfg; this.signer = signer;
        this.rest = new RestTemplate();
    }

    /** Tạo đơn ZaloPay (sandbox): trả về order_url cho client mở */
    public Map<String, Object> createOrder(Ticket booking, String appUser) {
        // Embed và item có thể tùy chọn
        Map<String, Object> embed = Map.of("redirecturl", ""); // có thể để client handle
        List<Map<String, Object>> items = booking.getSeats().stream()
                .map(s -> Map.<String, Object>of(
                        "itemid", s,
                        "itemname", s,
                        "itemprice", booking.getAmount() / booking.getSeats().size(),
                        "itemquantity", 1))
                .toList();

        long appTime = System.currentTimeMillis();
        // app_trans_id theo format YYMMDD_xxxxxx (unique/ngày)
        String yymmdd = DateTimeFormatter.ofPattern("yyMMdd").withZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(Instant.now());
        String appTransId = yymmdd + "_" + booking.getBookingCode(); // gọn gàng, duy nhất theo bookingCode

        String data = cfg.getAppId() + "|" + appTransId + "|" + appUser + "|" + booking.getAmount()
                + "|" + appTime + "|" + signer.json(embed) + "|" + signer.json(items);
        String mac = signer.hmacSHA256(cfg.getKey1(), data);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("app_id", cfg.getAppId());
        body.put("app_user", appUser);
        body.put("app_time", appTime);
        body.put("amount", booking.getAmount());      // VND (không *100)
        body.put("app_trans_id", appTransId);
        body.put("embed_data", embed);
        body.put("item", items);
        body.put("description", "Booking " + booking.getBookingCode());
        body.put("bank_code", "zalopayapp");          // sandbox: ví Zalo
        body.put("callback_url", cfg.getCallbackIpn());
        body.put("mac", mac);

        ResponseEntity<Map> res = rest.postForEntity(cfg.getEndpointCreate(), body, Map.class);
        Map<String,Object> ret = res.getBody();
        if (ret == null || ((Integer)ret.getOrDefault("return_code", -1)) != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ZaloPay create order failed: " + ret);
        }
        // Trả cho client mở order_url (hoặc dùng zp_trans_token)
        return Map.of(
                "order_url", ret.get("order_url"),
                "zp_trans_token", ret.get("zp_trans_token"),
                "app_trans_id", appTransId
        );
    }

    /** Verify IPN: ZaloPay gửi ?data=<json>&mac=<hmac> (GET/POST đều có thể) */
    public Map<String,Object> verifyIpn(String data, String mac) {
        String calc = signer.hmacSHA256(cfg.getKey2(), data);
        if (!calc.equalsIgnoreCase(mac)) {
            return Map.of("return_code", -1, "return_message", "mac not equal");
        }
        // parse data => chứa app_trans_id, zp_trans_id, amount, status=1 (success)
        try {
            Map<String,Object> parsed = new ObjectMapper().readValue(data, new TypeReference<Map<String,Object>>(){});
            return Map.of("return_code", 1, "return_message", "success", "parsed", parsed);
        } catch (Exception e) {
            return Map.of("return_code", -1, "return_message", "json parse error");
        }
    }

    /** Refund (sandbox). amount: số tiền hoàn (<= đã thanh toán) */
    public Map<String,Object> refund(int appId, String key1, String zpTransId, long amount, String desc) {
        long timestamp = System.currentTimeMillis();
        // mac format theo tài liệu: mac = HMAC_SHA256(key1, app_id|zp_trans_id|amount|description|timestamp)
        String data = appId + "|" + zpTransId + "|" + amount + "|" + desc + "|" + timestamp;
        String mac = signer.hmacSHA256(key1, data);

        Map<String,Object> req = new LinkedHashMap<>();
        req.put("app_id", appId);
        req.put("zp_trans_id", zpTransId);
        req.put("amount", amount);
        req.put("description", desc);
        req.put("timestamp", timestamp);
        req.put("mac", mac);

        ResponseEntity<Map> res = rest.postForEntity(cfg.getEndpointRefund(), req, Map.class);
        Map<String,Object> body = res.getBody();
        if (body == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty refund response");
        return body; // return_code == 1 là success (sandbox)
    }

    public String getKey1(){ return cfg.getKey1(); }
    public int getAppId(){ return cfg.getAppId(); }
}
