package com.ntth.spring_boot_heroku_cinema_app.service;

import com.ntth.spring_boot_heroku_cinema_app.pojo.SeatLock;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Ticket;
import com.ntth.spring_boot_heroku_cinema_app.repository.SeatLedgerRepository;
import com.ntth.spring_boot_heroku_cinema_app.repository.SeatLockRepository;
import com.ntth.spring_boot_heroku_cinema_app.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class TicketService {
    private final SeatLockRepository lockRepo;
    private final TicketRepository ticketRepo;
    private final SeatLedgerRepository ledgerRepo;
    private final ZaloPayService zalo;
    private final MongoTemplate mongo;

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
     * - Tạo booking (PENDING_PAYMENT hoặc CONFIRMEDnếu CASH)
     * - Chuyển ledger HOLD->CONFIRMED atomically (nếu paymentMethod=CASH)
     *
     * @param holdId id của seat lock
     * @param userId chủ sở hữu
     * @param paymentMethod "CASH" | "ZALOPAY"
     */
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
        try { b.setAmount(hold.getAmount()); } catch (Throwable ignore) {}
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
        Logger log = LoggerFactory.getLogger(getClass());

        // -------- 1) Lấy & kiểm tra input tối thiểu --------
        String data = req.get("data");
        String mac  = req.get("mac");
        if (data == null || mac == null) {
            // Thiếu dữ liệu IPN -> vẫn trả 200 ở controller, nhưng nội bộ bỏ qua
            log.warn("IPN missing data or mac");
            return;
        }

        // -------- 2) Verify chữ ký IPN từ ZaloPay SDK --------
        Map<String, Object> v = zalo.verifyIpn(data, mac);
        int rc = (int) v.getOrDefault("return_code", -1);
        if (rc != 1) {
            log.warn("IPN verify failed return_code={}", rc);
            return; // không throw để tránh ZP retry quá nhiều
        }

        // -------- 3) Parse payload --------
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) v.get("parsed");
        if (parsed == null) {
            log.warn("IPN parsed payload is null");
            return;
        }

        String appTransId = String.valueOf(parsed.get("app_trans_id"));  // vd: 250919_UIN-CTWE43
        if (appTransId == null || appTransId.isEmpty() || !appTransId.contains("_")) {
            log.warn("IPN invalid app_trans_id={}", appTransId);
            return;
        }
        String bookingCode = appTransId.substring(appTransId.indexOf('_') + 1);

        // -------- 4) Tìm booking --------
        Ticket b = ticketRepo.findByBookingCode(bookingCode).orElse(null);
        if (b == null) {
            log.warn("IPN booking not found bookingCode={}", bookingCode);
            return;
        }

        // Idempotent: đã CONFIRMED thì thôi
        if ("CONFIRMED".equalsIgnoreCase(b.getStatus())) {
            log.info("IPN ignored; already CONFIRMED booking={}", bookingCode);
            return;
        }

        // -------- 5) Lấy status & amount từ IPN --------
        int zpStatus;
        try {
            zpStatus = ((Number) parsed.getOrDefault("status", 0)).intValue();
        } catch (Throwable ignore) {
            zpStatus = 0;
        }

        long amountFromZp;
        try {
            amountFromZp = ((Number) parsed.get("amount")).longValue();
        } catch (Throwable t) {
            try {
                amountFromZp = Long.parseLong(String.valueOf(parsed.get("amount")));
            } catch (Exception e) {
                log.warn("IPN amount missing/invalid booking={}", bookingCode);
                return;
            }
        }

        // -------- 6) So sánh amount (an toàn kiểu dữ liệu) --------
        long bookingAmount;
        Object amtObj = b.getAmount();
        try {
            bookingAmount = ((Number) amtObj).longValue();
        } catch (Throwable t) {
            try {
                bookingAmount = Long.parseLong(String.valueOf(amtObj));
            } catch (Exception e) {
                log.warn("IPN cannot parse booking amount booking={} raw={}", bookingCode, String.valueOf(amtObj));
                return;
            }
        }
        if (bookingAmount != amountFromZp) {
            log.warn("IPN amount mismatch booking={} bookingAmt={} zpAmt={}", bookingCode, bookingAmount, amountFromZp);
            // Đánh FAILED để FE thấy rõ; không throw để tránh retry
            try { b.setStatus("FAILED"); ticketRepo.save(b); } catch (Throwable ignore) {}
            return;
        }

        // -------- 7) Cập nhật PaymentInfo chung --------
        Ticket.PaymentInfo pay = b.getPayment();
        if (pay == null) {
            pay = new Ticket.PaymentInfo();
            pay.setGateway("ZALOPAY");
        }
        Object zptObj = parsed.get("zp_trans_id");
        if (zptObj != null) {
            try { pay.setTxId(String.valueOf(zptObj)); } catch (Throwable ignore) {}
            try { pay.setZpTransId(String.valueOf(zptObj)); } catch (Throwable ignore) {}
        }
        try { pay.setRaw(parsed); } catch (Throwable ignore) {}
        b.setPayment(pay);

        // -------- 8) Nhánh trạng thái --------
        if (zpStatus == 1) {
            // Thành công
            b.setStatus("CONFIRMED");
            try { pay.setPaidAt(Instant.now()); } catch (Throwable ignore) {}
            ticketRepo.save(b);

            // Xác nhận ghế trong ledger
            long updated = ledgerRepo.confirmMany(b.getShowtimeId(), b.getSeats(), b.getId(), b.getHoldId());
            if (updated != b.getSeats().size()) {
                log.error("IPN seat confirm mismatch booking={} updated={} expected={}",
                        bookingCode, updated, b.getSeats().size());
                // Trong trường hợp hiếm, đánh FAILED để FE biết, bạn có thể chọn rollback hoặc manual fix
                b.setStatus("FAILED");
                ticketRepo.save(b);
                return;
            }

            // Xoá seat lock (nếu còn)
            try { lockRepo.deleteById(b.getHoldId()); } catch (Throwable ignore) {}

            log.info("IPN CONFIRMED booking={} seats={} showtime={}", bookingCode, b.getSeats(), b.getShowtimeId());
            return;
        }

        // status = 0 -> pending/processing: KHÔNG huỷ, giữ nguyên PENDING_PAYMENT
        if (zpStatus == 0) {
            if (!"PENDING_PAYMENT".equalsIgnoreCase(b.getStatus())) {
                b.setStatus("PENDING_PAYMENT");
                ticketRepo.save(b);
            } else {
                // vẫn lưu raw để trace
                try { ticketRepo.save(b); } catch (Throwable ignore) {}
            }
            log.info("IPN pending (status=0) booking={} keep PENDING_PAYMENT", bookingCode);
            return;
        }

        // Các trạng thái thất bại/hoàn/đảo (tuỳ tài liệu ZP) -> huỷ
        // Ví dụ: -1 (fail), 2 (refund), 3 (chargeback/reversal) — điều chỉnh theo tài liệu của bạn
        b.setStatus("CANCELED");
        try { ticketRepo.save(b); } catch (Throwable ignore) {}
        // (Tuỳ business) Có thể release ghế nếu đã lock
        try {
            // Nếu bạn có hàm release riêng:
            // ledgerRepo.releaseSeats(b.getShowtimeId(), b.getSeats(), b.getHoldId());
            lockRepo.deleteById(b.getHoldId());
        } catch (Throwable ignore) {}
        log.info("IPN set CANCELED booking={} status={}", bookingCode, zpStatus);
    }

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

        //3) Tạo ticket trạng thái CONFIRMED (snapshot số tiền từ hold)
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
     * Hủy vé: Gọi API hoàn tiền (refund) toàn phần của ZaloPay và trả ghế về trạng thái FREE
     * @param bookingId Mã định danh của vé (ID của Ticket)
     * @param reason Lý do hủy vé (có thể null, mặc định là "refund")
     * @return Đối tượng Ticket đã được cập nhật trạng thái
     * @throws ResponseStatusException Nếu vé không tồn tại, không ở trạng thái CONFIRMED, không sử dụng ZaloPay, hoặc refund thất bại
     */
    @Transactional
    public Ticket cancelBooking(String bookingId, String reason) {
        Logger log = LoggerFactory.getLogger(getClass());

        Ticket b = ticketRepo.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));

        if (!"CONFIRMED".equalsIgnoreCase(b.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ONLY_CONFIRMED_CAN_BE_CANCELED");
        }

        String cancelReason = (reason == null || reason.isBlank()) ? "cancel" : reason.trim();

        String gateway = null;
        try { gateway = (b.getPayment() != null ? b.getPayment().getGateway() : null); } catch (Throwable ignore) {}

        if ("ZALOPAY".equalsIgnoreCase(gateway)) {
            String zpTransId = null;
            try { zpTransId = b.getPayment().getZpTransId(); } catch (Throwable ignore) {}
            if (zpTransId == null || zpTransId.isBlank()) {
                try { zpTransId = b.getPayment().getTxId(); } catch (Throwable ignore) {}
            }

            if (zpTransId == null || zpTransId.isBlank()) {
                // Thiếu transaction id -> DEV: skip refund
                // Nếu cần chặt chẽ thì throw 400 "ZP_TRANS_ID_MISSING"
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
                try { rc = (rcObj instanceof Number) ? ((Number) rcObj).intValue() : Integer.parseInt(String.valueOf(rcObj)); }
                catch (Exception e) { rc = -999; }

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
