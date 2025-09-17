package com.ntth.spring_boot_heroku_cinema_app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Ticket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ZaloPayService {

    private static final Logger log = LoggerFactory.getLogger(ZaloPayService.class);
    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${zalopay.appId}")
    private int appId;

    @Value("${zalopay.key1}")
    private String key1;

    @Value("${zalopay.key2}")
    private String key2;

    @Value("${zalopay.endpoint.create}")
    private String createUrl;     // must be absolute: https://...

    @Value("${zalopay.endpoint.refund}")
    private String refundUrl;

    @Value("${zalopay.callback.ipn}")
    private String ipnCallbackUrl;

    @Value("${app.publicBaseUrl:http://localhost:8080}")   // (tuỳ chọn) nếu code còn dùng
    private String publicBaseUrl;

    @Value("${app.deeplink:}")  // ví dụ: myapp://zp-callback
    private String deeplinkBase;

    /** Tạo đơn đặt hàng ZaloPay, trả order_url cho FE mở app. */
    public Map<String, Object> createOrder(Ticket b, String appUser) {
        String redirect;
        if (deeplinkBase != null && !deeplinkBase.isBlank()) {
            String sep = deeplinkBase.contains("?") ? "&" : "?";
            redirect = deeplinkBase + sep + "bookingId=" + b.getId();
        } else {
            redirect = ensureNoTrailingSlash(publicBaseUrl)
                    + "/api/payments/zalopay/return?bookingId=" + b.getId();
        }
        String cancel = redirect + (redirect.contains("?") ? "&" : "?") + "canceled=1";

        URI endpoint = absoluteHttpUrl(createUrl);

        String appTransId = YYMMDD.format(LocalDate.now()) + "_" + b.getBookingCode();

        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("redirecturl", ensureNoTrailingSlash(publicBaseUrl) + "/payments/zalopay/return");
        embed.put("cancelurl",   cancel);
        embed.put("merchantinfo", "bookingId=" + b.getId());
        String embedData = toJson(embed);

        long appTime = System.currentTimeMillis();
        String amountStr = String.valueOf(b.getAmount());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("app_id", String.valueOf(appId));
        form.add("app_user", appUser);
        form.add("app_time", String.valueOf(appTime));
        form.add("amount", amountStr);
        form.add("app_trans_id", appTransId);
        form.add("embed_data", embedData);
        form.add("item", "[]");
        form.add("description", "Pay for booking " + b.getBookingCode());
        form.add("callback_url", ipnCallbackUrl);

        // MAC: HMAC_SHA256(key1, app_id|app_trans_id|app_user|amount|app_time|embed_data|item)
        String dataMac = String.join("|",
                form.getFirst("app_id"),
                form.getFirst("app_trans_id"),
                form.getFirst("app_user"),
                form.getFirst("amount"),
                form.getFirst("app_time"),
                form.getFirst("embed_data"),
                form.getFirst("item")
        );
        String mac = hmacSHA256Hex(key1, dataMac);
        form.add("mac", mac);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<Map> res = restTemplate.postForEntity(endpoint, new HttpEntity<>(form, headers), Map.class);
        if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ZP_CREATE_ORDER_FAILED");
        }

        Map<String, Object> body = castToMap(res.getBody());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("order_url", body.get("order_url"));
        out.put("zp_trans_token", body.get("zp_trans_token"));
        out.put("app_trans_id", appTransId);
        return out;
    }

    /** Xác minh IPN: data & mac (key2). Return {return_code, return_message, parsed?} */
    public Map<String, Object> verifyIpn(String data, String mac) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (data == null || data.isBlank() || mac == null || mac.isBlank()) {
            out.put("return_code", -1);
            out.put("return_message", "missing data/mac");
            return out;
        }
        String macCalc = hmacSHA256Hex(key2, data);
        if (!macCalc.equalsIgnoreCase(mac)) {
            out.put("return_code", -1);
            out.put("return_message", "invalid mac");
            return out;
        }
        Map<String, Object> parsed = parseJsonToMap(data);
        out.put("return_code", 1);
        out.put("return_message", "ok");
        out.put("parsed", parsed);
        return out;
    }

    /** Refund (sandbox/prod). amount phải <= amount đã thanh toán */
    public Map<String, Object> refund(String zpTransId, long amount, String description) {
        if (zpTransId == null || zpTransId.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "zpTransId is required");
        if (amount <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be > 0");

        URI endpoint = absoluteHttpUrl(refundUrl);

        long timestamp = System.currentTimeMillis();
        String desc = (description == null || description.isBlank()) ? "Refund booking" : description.trim();

        // mac = HMAC_SHA256(key1, app_id|zp_trans_id|amount|description|timestamp)
        String data = appId + "|" + zpTransId + "|" + amount + "|" + desc + "|" + timestamp;
        String mac = hmacSHA256Hex(key1, data);

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("app_id", appId);
        req.put("zp_trans_id", zpTransId);
        req.put("amount", amount);
        req.put("description", desc);
        req.put("timestamp", timestamp);
        req.put("mac", mac);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> res = restTemplate.postForEntity(endpoint, new HttpEntity<>(req, headers), Map.class);
        if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ZP_REFUND_FAILED");
        }
        return castToMap(res.getBody());
    }

    // ===== Helpers =====
    private static URI absoluteHttpUrl(String url) {
        try { return UriComponentsBuilder.fromHttpUrl(url).build(true).toUri(); }
        catch (Exception e) { throw new IllegalStateException("Invalid absolute URL: " + url, e); }
    }

    private String ensureNoTrailingSlash(String base) {
        if (base == null) return "";
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { throw new IllegalStateException("JSON serialize error", e); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToMap(Object obj) {
        if (obj instanceof Map) return (Map<String, Object>) obj;
        return objectMapper.convertValue(obj, new TypeReference<Map<String, Object>>() {});
    }

    private Map<String, Object> parseJsonToMap(String json) {
        try { return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}); }
        catch (Exception e) { throw new IllegalStateException("JSON parse error", e); }
    }

    private static String hmacSHA256Hex(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 error", e);
        }
    }
}
