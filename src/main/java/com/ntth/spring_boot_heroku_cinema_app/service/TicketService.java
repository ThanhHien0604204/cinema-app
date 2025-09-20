package com.ntth.spring_boot_heroku_cinema_app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntth.spring_boot_heroku_cinema_app.filter.JwtUser;
import com.ntth.spring_boot_heroku_cinema_app.pojo.SeatLock;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Ticket;
import com.ntth.spring_boot_heroku_cinema_app.repository.SeatLedgerRepository;
import com.ntth.spring_boot_heroku_cinema_app.repository.SeatLockRepository;
import com.ntth.spring_boot_heroku_cinema_app.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Locale;

@Service
public class TicketService {
    private final SeatLockRepository lockRepo;
    private final TicketRepository ticketRepo;
    private final SeatLedgerRepository ledgerRepo;
    private final ZaloPayService zalo;
    private final MongoTemplate mongo;
    private static final Logger log = LoggerFactory.getLogger(TicketService.class); // Sửa logger

    @Value("${momo.secretKey:K951B6PE1waDMi640xX08PD3vg6EkVlz}")
    private String momoSecretKey;

    public TicketService(SeatLockRepository lockRepo,
                         TicketRepository bookingRepo,
                         SeatLedgerRepository ledgerRepo,
                         ZaloPayService zalo,
                         MongoTemplate mongo) {
        this.lockRepo = lockRepo;
        this.ticketRepo = bookingRepo;
        this.ledgerRepo = ledgerRepo;
        this.zalo = zalo;
        this.mongo = mongo;
    }

    /**
     * Tạo booking từ 1 hold có sẵn:
     * - Kiểm tra hold còn hạn
     * - Tạo booking (PENDING_PAYMENT hoặc CONFIRMED nếu CASH)
     * - Chuyển ledger HOLD->CONFIRMED atomically (nếu paymentMethod=CASH)
     *
     * @param holdId id của seat lock
     * @param userId chủ sở hữu
     * @param paymentMethod "CASH" | "ZALOPAY"
     */
    @Transactional
    public Ticket createBookingFromHold(String holdId, String userId, String paymentMethod) {
        // 1) Load hold
        SeatLock hold = lockRepo.findById(holdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE, "HOLD_EXPIRED_OR_NOT_FOUND"));

        // 1.1) Chủ sở hữu
        if (!Objects.equals(hold.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
        // 1.2) Chưa hết hạn
        if (hold.getExpiresAt() == null || hold.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "HOLD_EXPIRED");
        }

        // 2) Rẽ nhánh theo phương thức thanh toán
        String method = (paymentMethod == null ? "" : paymentMethod).trim().toUpperCase(Locale.ROOT);
        if ("ZALOPAY".equals(method)) {
            // Giữ nguyên flow ZaloPay: tạo booking PENDING_PAYMENT, xác nhận qua IPN
            return createBookingZaloPay(holdId, userId);
        }

        // 3) Tạo ticket trạng thái CONFIRMED (snapshot số tiền từ hold)
        if ("CASH".equals(method) || method.isEmpty()) {
            String bookingCode = genCode();

            Ticket b = new Ticket();
            b.setUserId(userId);
            b.setShowtimeId(hold.getShowtimeId());
            b.setSeats(new ArrayList<>(hold.getSeats()));
            b.setAmount(hold.getAmount());      // SeatLock có getAmount()
            b.setHoldId(holdId);
            b.setBookingCode(bookingCode);
            b.setStatus("CONFIRMED");
            b.setCreatedAt(Instant.now());
            b = ticketRepo.save(b);

            // 3.2) Đổi ledger: HOLD -> CONFIRMED theo bookingId & holdId (đảm bảo đúng chủ hold + còn hạn)
            long updated = ledgerRepo.confirmMany(hold.getShowtimeId(), hold.getSeats(), b.getId(), holdId);
            if (updated != hold.getSeats().size()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "SEAT_TAKEN_OR_HOLD_MISMATCH");
            }
            // 3.3) Xoá SeatLock
            lockRepo.deleteById(holdId);
            return b;
        }

        // 5) Không hỗ trợ phương thức khác
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PAYMENT_METHOD");
    }

    /**
     * Tạo booking từ hold: trạng thái PENDING_PAYMENT (ZaloPay)
     * =>tạo PENDING_PAYMENT và đợi IPN xác nhận
     */
    @Transactional
    public Ticket createBookingZaloPay(String holdId, String userId) {
        // 1) Load hold
        SeatLock hold = lockRepo.findById(holdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE, "HOLD_EXPIRED_OR_NOT_FOUND"));

        // 1.1) Kiểm tra chủ sở hữu (nếu cần)
        if (!Objects.equals(hold.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FORBIDDEN");
        }

        // 1.2) Check hết hạn
        if (hold.getExpiresAt() == null || hold.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "HOLD_EXPIRED");
        }

        // 2) Tạo booking code CASH/DEFAULT: xác nhận ngay
        String bookingCode = genCode(); // ví dụ UIN-XXXXXX

        // 3) Lập booking ban đầu
        Ticket b = new Ticket();
        b.setBookingCode(bookingCode);
        b.setUserId(userId);
        b.setShowtimeId(hold.getShowtimeId());
        b.setSeats(new ArrayList<>(hold.getSeats()));
        try {
            b.setAmount(hold.getAmount());
        } catch (Throwable ignore) {}
        b.setHoldId(holdId);
        b.setStatus("PENDING_PAYMENT");
        b.setCreatedAt(Instant.now());

        // (nếu Ticket có inner class PaymentInfo)
        try {
            Ticket.PaymentInfo payment = new Ticket.PaymentInfo();
            payment.setGateway("ZALOPAY");
            b.setPayment(payment);
        } catch (Throwable ignore) {
            // Nếu không có PaymentInfo trong model của bạn thì bỏ qua block này
        }

        // 4) Lưu booking
        return ticketRepo.save(b);
    }

    /** Tạo booking dùng cổng MoMo: từ holdId -> Ticket PENDING_PAYMENT */
    @Transactional
    public Ticket createBookingMoMo(String holdId, JwtUser user) {
        // Giống createBookingZaloPay của bạn:
        //  - kiểm tra holdId hợp lệ, thuộc user
        //  - sinh bookingCode
        //  - tạo Ticket status=PENDING_PAYMENT, payment.gateway="MOMO", lưu holdId để confirm ledger

        SeatLock lock = lockRepo.findById(holdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "HOLD_NOT_FOUND"));

        // (tuỳ yêu cầu: kiểm tra owner lock == user)
        Ticket t = new Ticket();
        t.setUserId(user.getUserId());
        t.setShowtimeId(lock.getShowtimeId());
        t.setSeats(lock.getSeats());
        try {
            t.setAmount(lock.getAmount());
        } catch (Exception ignore) {}
        t.setStatus("PENDING_PAYMENT");
        t.setHoldId(lock.getId());
        t.setBookingCode(genCode());
        t.setCreatedAt(Instant.now());

        Ticket.PaymentInfo p = new Ticket.PaymentInfo();
        p.setGateway("MOMO");
        t.setPayment(p);

        return ticketRepo.save(t);
    }

    /**
     * Gọi từ Controller để lấy order_url
     */
    public Map<String, Object> createZpOrderLink(String bookingId, String appUser) {
        Ticket b = ticketRepo.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));
        if (!"PENDING_PAYMENT".equals(b.getStatus()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BOOKING_NOT_PENDING");
        return zalo.createOrder(b, appUser);
    }

    /**
     * Xác nhận IPN từ ZaloPay (status=1 là thanh toán thành công) để xác nhận thanh toán và cập nhật trạng thái vé
     → xác minh thành công ⇒ b.setStatus("CONFIRMED")
     */
    @Transactional
    public void handleZpIpn(Map<String, String> req) {
        // -------- 1) Lấy data/mac & verify chữ ký -------- (giữ nguyên)
        String data = req.get("data");
        String mac  = req.get("mac");
        if (data == null || mac == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MISSING_DATA_OR_MAC");
        }

        Map<String, Object> verify = zalo.verifyIpn(data, mac);
        int returnCode = (int) verify.getOrDefault("return_code", -1);
        if (returnCode != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_IPN");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) verify.get("parsed");
        if (parsed == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PARSE_FAILED");
        }

        // -------- 2) Lấy app_trans_id -> bookingCode -------- (giữ nguyên)
        String appTransId = String.valueOf(parsed.get("app_trans_id"));
        if (appTransId == null || appTransId.isEmpty() || !appTransId.contains("_")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_APP_TRANS_ID");
        }
        String bookingCode = appTransId.split("_", 2)[1];

        Ticket b = ticketRepo.findByBookingCode(bookingCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));

        // Đảm bảo có PaymentInfo (giữ nguyên)
        Ticket.PaymentInfo pay = b.getPayment();
        if (pay == null) {
            pay = new Ticket.PaymentInfo();
            pay.setGateway("ZALOPAY");
            b.setPayment(pay);
        }

        // Ghi zp_trans_id/txId (giữ nguyên)
        Object zptObj = parsed.get("zp_trans_id");
        if (zptObj != null) {
            String zpt = String.valueOf(zptObj);
            pay.setTxId(zpt);
            try { pay.setZpTransId(zpt); } catch (Throwable ignore) {}
        }
        pay.setRaw(parsed);

        // -------- 3) Đọc status/amount từ IPN -------- (giữ nguyên)
        int statusFromZp = safeInt(parsed.get("status"), -1);
        long amountFromZp = 0L;
        Object amountObj = parsed.get("amount");
        if (amountObj instanceof Number) {
            amountFromZp = ((Number) amountObj).longValue();
        } else if (amountObj != null) {
            try { amountFromZp = Long.parseLong(String.valueOf(amountObj)); } catch (Exception ignore) {}
        }

        // -------- 4) Check amount an toàn -------- (giữ nguyên)
        Long bookingAmount = b.getAmount();
        if (bookingAmount != null && amountFromZp > 0 && bookingAmount.longValue() != amountFromZp) {
            log.warn("IPN amount mismatch booking={} zp={}", bookingAmount, amountFromZp);
            try {
                b.setStatus("FAILED");
                ticketRepo.save(b);
            } catch (Throwable ignore) {}
            return;
        }

        // -------- 5) THAY ĐỔI LOGIC: status=0 hoặc 1 đều CONFIRMED ngay --------
        if (statusFromZp == 0 || statusFromZp == 1) {
            // CẢ PENDING VÀ SUCCESS ĐỀU CONFIRMED NGAY LẬP TỨC
            if (!"CONFIRMED".equalsIgnoreCase(b.getStatus())) {
                b.setStatus("CONFIRMED");
                pay.setPaidAt(Instant.now());
                ticketRepo.save(b);

                // Confirm ghế trong ledger
                long updated = ledgerRepo.confirmMany(b.getShowtimeId(), b.getSeats(), b.getId(), b.getHoldId());
                if (updated != (b.getSeats() == null ? 0 : b.getSeats().size())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "SEAT_TAKEN_OR_HOLD_MISMATCH");
                }

                // Xóa hold
                try { lockRepo.deleteById(b.getHoldId()); } catch (Throwable ignore) {}

                log.info("IPN CONFIRMED: bookingId={}, statusFromZp={}, holdId={}, seats={}",
                        b.getId(), statusFromZp, b.getHoldId(), b.getSeats());
            }
            return;
        }

        // Các status còn lại (2, 3, ...) coi như fail
        if (!"CONFIRMED".equalsIgnoreCase(b.getStatus())) {
            b.setStatus("CANCELED");
            ticketRepo.save(b);
        }
        log.debug("IPN not success (status={}) booking={} -> CANCELED", statusFromZp, bookingCode);
    }

    @Transactional
    public void handleMomoIpn(Map<String, Object> ipn) {
        // 1) Lấy các field chính
        String partnerCode = String.valueOf(ipn.get("partnerCode"));
        String accessKey   = String.valueOf(ipn.get("accessKey"));
        String orderId     = String.valueOf(ipn.get("orderId"));     // mình dùng bookingCode
        String requestId   = String.valueOf(ipn.get("requestId"));
        String amountStr   = String.valueOf(ipn.get("amount"));
        String orderInfo   = String.valueOf(ipn.get("orderInfo"));
        String orderType   = String.valueOf(ipn.get("orderType"));
        String transId     = String.valueOf(ipn.get("transId"));
        String resultCode  = String.valueOf(ipn.get("resultCode"));
        String message     = String.valueOf(ipn.get("message"));
        String payType     = String.valueOf(ipn.get("payType"));
        String responseTime= String.valueOf(ipn.get("responseTime"));
        String extraData   = String.valueOf(ipn.get("extraData"));
        String signature   = String.valueOf(ipn.get("signature"));

        // 2) Verify signature
        String raw =
                "accessKey="+accessKey+
                        "&amount="+amountStr+
                        "&extraData="+extraData+
                        "&message="+message+
                        "&orderId="+orderId+
                        "&orderInfo="+orderInfo+
                        "&orderType="+orderType+
                        "&partnerCode="+partnerCode+
                        "&payType="+payType+
                        "&requestId="+requestId+
                        "&responseTime="+responseTime+
                        "&resultCode="+resultCode+
                        "&transId="+transId;

        // Sửa: import đúng class và gọi method static
        String expected = MomoService.hmacSHA256(raw, this.momoSecretKey);
        if (!expected.equals(signature)) {
            // Sai chữ ký -> bỏ qua
            log.warn("MoMo IPN invalid signature orderId={}", orderId);
            return;
        }

        // 3) Tìm booking theo bookingCode (orderId)
        Ticket b = ticketRepo.findByBookingCode(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));

        // 4) Parse amount from MoMo
        long amountFromMomo = 0L;
        try {
            amountFromMomo = Long.parseLong(amountStr);
        } catch (NumberFormatException ignore) {}

        // 4) Kiểm tiền
        Long amountInDb = b.getAmount(); // có thể null

        if (amountInDb != null && amountFromMomo > 0 && amountInDb.longValue() != amountFromMomo) {
            log.warn("IPN amount mismatch booking={} db={} ipn={}", b.getBookingCode(), amountInDb, amountFromMomo);
            try {
                b.setStatus("FAILED");
                ticketRepo.save(b);
            } catch (Throwable ignore) {}
            return;
        }

        // 5) Kết quả
        int rc;
        try {
            rc = Integer.parseInt(resultCode);
        } catch (NumberFormatException e) {
            rc = -1;
        }

        if (rc == 0) {
            // PAID
            if ("CONFIRMED".equals(b.getStatus())) return; // idempotent

            b.setStatus("CONFIRMED");
            Ticket.PaymentInfo p = b.getPayment() != null ? b.getPayment() : new Ticket.PaymentInfo();
            p.setGateway("MOMO");
            p.setPaidAt(Instant.now());
            p.setTxId(transId);
            p.setRaw(ipn);
            b.setPayment(p);
            ticketRepo.save(b);

            long updated = ledgerRepo.confirmMany(b.getShowtimeId(), b.getSeats(), b.getId(), b.getHoldId());
            if (updated != (b.getSeats() == null ? 0 : b.getSeats().size())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "SEAT_TAKEN_OR_HOLD_MISMATCH");
            }
            try { lockRepo.deleteById(b.getHoldId()); } catch (Throwable ignore) {}

        } else if (rc == 1006 || rc == 9000) {
            // pending / user paying — giữ nguyên PENDING_PAYMENT
            log.debug("MoMo IPN pending rc={} booking={}", rc, b.getBookingCode());
        } else {
            // Failed / canceled
            b.setStatus("CANCELED");
            ticketRepo.save(b);
            // (tuỳ bạn có release hold hay để cron dọn)
        }
    }

    /** IPN MoMo: thanh toán thành công */
    @Transactional
    public void handleMomoIpnPaid(String bookingId, String bookingCode, long amountFromMomo,
                                  String transId, Map<String, Object> raw) {
        Logger log = LoggerFactory.getLogger(getClass());

        // Tìm booking theo bookingId hoặc bookingCode
        Ticket b = findByEither(bookingId, bookingCode);
        if (b == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND");
        }

        // Idempotent
        if ("CONFIRMED".equalsIgnoreCase(b.getStatus())) return;

        // --- Kiểm tra khớp tiền (null-safe) ---
        Long amountInDb = b.getAmount();
        if (amountInDb != null && amountFromMomo > 0L && amountInDb.longValue() != amountFromMomo) {
            log.warn("MoMo IPN amount mismatch bookingCode={} db={} ipn={}",
                    b.getBookingCode(), amountInDb, amountFromMomo);
            try {
                b.setStatus("FAILED");
                ticketRepo.save(b);
            } catch (Throwable ignore) {}
            return;
        }

        // --- Cập nhật thông tin thanh toán ---
        Ticket.PaymentInfo p = b.getPayment();
        if (p == null) p = new Ticket.PaymentInfo();
        p.setGateway("MOMO");
        if (transId != null) p.setTxId(transId);    // dùng txId chung (không cần setMomoTransId)
        p.setPaidAt(Instant.now());
        try { p.setRaw(raw); } catch (Throwable ignore) {}
        b.setPayment(p);

        // --- Xác nhận vé ---
        b.setStatus("CONFIRMED");
        ticketRepo.save(b);

        // --- Confirm ledger + xoá hold ---
        long updated = ledgerRepo.confirmMany(b.getShowtimeId(), b.getSeats(), b.getId(), b.getHoldId());
        if (updated != b.getSeats().size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SEAT_TAKEN_OR_HOLD_MISMATCH");
        }
        try { lockRepo.deleteById(b.getHoldId()); } catch (Throwable ignore) {}
    }

    /** IPN MoMo: cancel/failed */
    @Transactional
    public void handleMomoIpnCanceled(String bookingId, String bookingCode, Map<String,Object> raw) {
        Ticket b = findByEither(bookingId, bookingCode);
        if (!"CONFIRMED".equalsIgnoreCase(b.getStatus())) {
            b.setStatus("CANCELED");
            if (b.getPayment() == null) b.setPayment(new Ticket.PaymentInfo());
            b.getPayment().setRaw(raw);
            ticketRepo.save(b);
        }
        // tuỳ chính sách: không confirm ledger; hold sẽ hết hạn hoặc bạn có API release
    }

    @Transactional
    public void handleMomoIpnFailed(String bookingId, String bookingCode, Map<String,Object> raw) {
        Ticket b = findByEither(bookingId, bookingCode);
        if (!"CONFIRMED".equalsIgnoreCase(b.getStatus())) {
            b.setStatus("FAILED");
            if (b.getPayment() == null) b.setPayment(new Ticket.PaymentInfo());
            b.getPayment().setRaw(raw);
            ticketRepo.save(b);
        }
    }

    // Helper: tìm theo bookingId hoặc bookingCode
    private Ticket findByEither(String bookingId, String bookingCode) {
        if (bookingId != null) {
            return ticketRepo.findById(bookingId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));
        }
        if (bookingCode != null) {
            return ticketRepo.findByBookingCode(bookingCode)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MISSING_BOOKING_REF");
    }

    private int safeInt(Object o, int def) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o != null) {
            try { return Integer.parseInt(String.valueOf(o)); } catch (Exception ignore) {}
        }
        return def;
    }

    /**
     * Hủy vé: Gọi API hoàn tiền (refund) toàn phần của ZaloPay và trả ghế về trạng thái FREE
     * @param bookingId Mã định danh của vé (ID của Ticket)
     * @param reason Lý do hủy vé (có thể null, mặc định là "refund")
     * @return Đối tượng Ticket đã được cập nhật trạng thái
     * @throws ResponseStatusException Nếu vé không tồn tại, không ở trạng thái CONFIRMED, không sử dụng ZaloPay, hoặc refund thất bại
     */
    @Transactional
    public Ticket cancelBooking(String bookingId, String reason) {
        // Sửa: dùng static logger
        // Logger log = LoggerFactory.getLogger(getClass());

        Ticket b = ticketRepo.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));

        if (!"CONFIRMED".equalsIgnoreCase(b.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ONLY_CONFIRMED_CAN_BE_CANCELED");
        }

        String cancelReason = (reason == null || reason.isBlank()) ? "cancel" : reason.trim();

        String gateway = null;
        try {
            gateway = (b.getPayment() != null ? b.getPayment().getGateway() : null);
        } catch (Throwable ignore) {}

        if ("ZALOPAY".equalsIgnoreCase(gateway)) {
            String zpTransId = null;
            try {
                zpTransId = b.getPayment().getZpTransId();
            } catch (Throwable ignore) {}
            if (zpTransId == null || zpTransId.isBlank()) {
                try {
                    zpTransId = b.getPayment().getTxId();
                } catch (Throwable ignore) {}
            }

            if (zpTransId == null || zpTransId.isBlank()) {
                // Thiếu transaction id -> DEV: skip refund
                // Nếu cần chặt chẽ thì throw 400 "ZP_TRANS_ID_MISSING"
                log.warn("Cannot refund: missing zp_trans_id for booking {}", bookingId);
            } else if (zpTransId.startsWith("TEST_")) {
                // >>> PATCH DEV: bỏ qua refund thật nếu là mã giả lập
                log.warn("Mock cancel: skip real ZaloPay refund for test id {}", zpTransId);
            } else {
                // Gọi refund thật (chữ ký MỚI)
                Map<String, Object> ret = zalo.refund(zpTransId, b.getAmount(), cancelReason);

                // Nếu dự án của bạn vẫn dùng chữ ký CŨ, thay dòng trên bằng:
                // Map<String, Object> ret = zalo.refund(zalo.getAppId(), zalo.getKey1(), zpTransId, b.getAmount(), cancelReason);

                Object rcObj = (ret != null ? ret.get("return_code") : null);
                int rc;
                try {
                    rc = (rcObj instanceof Number) ? ((Number) rcObj).intValue() : Integer.parseInt(String.valueOf(rcObj));
                }
                catch (Exception e) {
                    rc = -999;
                }

                if (rc != 1) {
                    log.error("ZaloPay refund failed: rc={}, resp={}", rc, ret);
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ZALO_REFUND_FAILED");
                }
            }
        }

        // Free seats (CONFIRMED -> FREE)
        try {
            ledgerRepo.freeMany(b.getShowtimeId(), b.getSeats(), b.getId());
        } catch (Throwable e) {
            log.warn("freeMany failed for booking {}: {}", b.getId(), e.toString());
        }

        b.setStatus("CANCELED");
        ticketRepo.save(b);
        return b;
    }

    /**
     * Tạo mã ngẫu nhiên theo định dạng UIN-XXXXXX
     * @return Chuỗi mã ngẫu nhiên, ví dụ: UIN-8F3K2N
     */
    private String genCode() {
        // 1. Định nghĩa tập hợp ký tự để tạo mã ngẫu nhiên
        // Loại bỏ các ký tự dễ nhầm lẫn như I, O, 0, 1 để tránh lỗi đọc
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

        // 2. Sử dụng ThreadLocalRandom để tạo số ngẫu nhiên hiệu quả
        ThreadLocalRandom r = ThreadLocalRandom.current();

        // 3. Tạo chuỗi mã với tiền tố "UIN-" và 6 ký tự ngẫu nhiên
        StringBuilder sb = new StringBuilder("UIN-");
        for (int i = 0; i < 6; i++) {
            // Lấy một ký tự ngẫu nhiên từ alphabet
            sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
        }
        // 4. Trả về chuỗi mã hoàn chỉnh
        return sb.toString();
    }
}