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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
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
        log.info("Received ZaloPay IPN: {}", req);
        String data = req.get("data");
        String mac = req.get("mac");
        if (data == null || mac == null) {
            log.error("Missing data or mac in IPN");
            return;
        }
        Map<String, Object> verify = zalo.verifyIpn(data, mac);
        int returnCode = (int) verify.getOrDefault("return_code", -1);
        if (returnCode != 1) {
            log.error("Invalid IPN signature: {}", verify);
            return;
        }
        Map<String, Object> parsed = (Map<String, Object>) verify.get("parsed");
        if (parsed == null) {
            log.error("Failed to parse IPN data");
            return;
        }
        String appTransId = String.valueOf(parsed.get("app_trans_id"));
        int status = ((Number) parsed.getOrDefault("status", -1)).intValue();
        log.info("IPN for app_trans_id={}, status={}", appTransId, status);
        String bookingId = req.get("bookingId");
        if (bookingId == null) {
            log.error("Missing bookingId in IPN");
            return;
        }
        Ticket ticket = ticketRepo.findById(bookingId).orElse(null);
        if (ticket == null || !"PENDING_PAYMENT".equals(ticket.getStatus())) {
            log.warn("Booking {} not found or not PENDING_PAYMENT", bookingId);
            return;
        }
        if (status == 0) {
            log.info("IPN pending (status=0) for app_trans_id={}, bookingId={}", appTransId, bookingId);
        } else if (status == 1) {
            log.info("IPN success (status=1) for app_trans_id={}, bookingId={}", appTransId, bookingId);
            ticket.setStatus("CONFIRMED");
            Ticket.PaymentInfo payment = ticket.getPayment();
            if (payment == null) {
                payment = new Ticket.PaymentInfo();
                ticket.setPayment(payment);
            }
            payment.setGateway("ZALOPAY");
            payment.setZpTransId(String.valueOf(parsed.get("zp_trans_id")));
            payment.setPaidAt(Instant.now());
            long updated = ledgerRepo.confirmMany(ticket.getShowtimeId(), ticket.getSeats(), bookingId, ticket.getHoldId());
            if (updated != ticket.getSeats().size()) {
                log.error("Failed to confirm seats for booking {}", bookingId);
                return;
            }
            lockRepo.deleteById(ticket.getHoldId());
            ticketRepo.save(ticket);
        } else if (status == -1) {
            log.info("IPN failed (status=-1) for app_trans_id={}, bookingId={}", appTransId, bookingId);
            ticket.setStatus("FAILED");
            ledgerRepo.freeMany(ticket.getShowtimeId(), ticket.getSeats(), bookingId);
            lockRepo.deleteById(ticket.getHoldId());
            ticketRepo.save(ticket);
        } else {
            log.warn("Unknown IPN status {} for app_trans_id={}, bookingId={}", status, appTransId, bookingId);
        }
    }

    private int safeInt(Object o, int def) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o != null) {
            try { return Integer.parseInt(String.valueOf(o)); } catch (Exception ignore) {}
        }
        return def;
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

    @Transactional
    public void confirmBooking(String bookingId, JwtUser user) {
        Logger log = LoggerFactory.getLogger(getClass());
        Ticket ticket = ticketRepo.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));
        if (!ticket.getUserId().equals(user.getUserId())) { // Sửa: So sánh userId thay vì email
            log.warn("Unauthorized attempt to confirm booking {} by user {}", bookingId, user.getUserId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "UNAUTHORIZED");
        }
        if (!"PENDING_PAYMENT".equals(ticket.getStatus())) {
            log.warn("Booking {} is not in PENDING_PAYMENT, current status: {}", bookingId, ticket.getStatus());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_STATUS");
        }
        ticket.setStatus("CONFIRMED");
        Ticket.PaymentInfo payment = ticket.getPayment();
        if (payment == null) {
            payment = new Ticket.PaymentInfo();
            ticket.setPayment(payment);
        }
        payment.setGateway("ZALOPAY");
        payment.setZpTransId("SANDBOX_" + System.currentTimeMillis());
        payment.setPaidAt(Instant.now()); // Thêm: Ghi thời gian thanh toán
        long updated = ledgerRepo.confirmMany(ticket.getShowtimeId(), ticket.getSeats(), bookingId, ticket.getHoldId());
        if (updated != ticket.getSeats().size()) {
            log.error("Failed to confirm seats for booking {}", bookingId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SEAT_TAKEN");
        }
        lockRepo.deleteById(ticket.getHoldId());
        ticketRepo.save(ticket);
        log.info("Booking {} confirmed successfully for user {}", bookingId, user.getUserId());
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
