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
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
        // 1. Lấy dữ liệu và chữ ký (mac) từ yêu cầu IPN
        String data = req.get("data"); // Dữ liệu giao dịch từ ZaloPay
        String mac = req.get("mac");   // Chữ ký để xác minh tính toàn vẹn của dữ liệu
        if (data == null || mac == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MISSING_DATA_OR_MAC");
        }

        // 2. Xác minh IPN bằng cách gọi hàm verifyIpn của ZaloPay SDK
        // Kết quả trả về một Map chứa "return_code" (1 = hợp lệ) và "parsed" (dữ liệu đã phân tích)
        //log.info("Verifying IPN with data: {}", data);
        Map<String, Object> v = zalo.verifyIpn(data, mac);

        // 3. Kiểm tra mã trả về (return_code), nếu không phải 1 thì ném lỗi
        if ((int) v.getOrDefault("return_code", -1) != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_IPN");
        }

        // 4. Lấy dữ liệu đã phân tích từ kết quả xác minh
        Map<String, Object> parsed = (Map<String, Object>) v.get("parsed");
        if (!parsed.containsKey("amount")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MISSING_AMOUNT");
        }

        // 5. Lấy app_trans_id (mã giao dịch ứng dụng, định dạng: yyMMdd_bookingCode)
        String appTransId = (String) parsed.get("app_trans_id");
        if (appTransId == null || appTransId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MISSING_APP_TRANS_ID");
        }

        // 6. Tách app_trans_id thành hai phần (ngày và bookingCode) bằng dấu "_"
        String[] parts = appTransId.split("_", 2);

        // 7. Kiểm tra định dạng app_trans_id, nếu không đúng (không có 2 phần) thì ném lỗi
        if (parts.length != 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_APP_TRANS_ID");
        }

        // 8. Lấy bookingCode từ phần thứ hai của app_trans_id
        String bookingCode = parts[1];
        //log.info("Processing IPN for bookingCode: {}", bookingCode);

        // 9. Tìm vé (Ticket) trong cơ sở dữ liệu dựa trên bookingCode
        Ticket b = ticketRepo.findByBookingCode(bookingCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));

        // 10. Kiểm tra trạng thái vé, nếu đã là CONFIRMED thì thoát hàm (đảm bảo idempotent)
        if ("CONFIRMED".equals(b.getStatus())) {
            //log.info("IPN already processed for bookingCode: {}", bookingCode);
            return; // Không xử lý lại nếu giao dịch đã được xác nhận
        }

        // 11. Lấy trạng thái giao dịch từ ZaloPay (status = 1 nghĩa là thanh toán thành công)
        int status = (int) parsed.getOrDefault("status", 0);

        // 12. Lấy số tiền thanh toán từ dữ liệu ZaloPay
        long amount = ((Number) parsed.get("amount")).longValue();

        // 13. So sánh số tiền thanh toán với số tiền của vé, nếu không khớp thì ném lỗi
        if (amount != b.getAmount()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "AMOUNT_MISMATCH");
        }

        // 14. Xử lý khi giao dịch thành công (status = 1)
        if (status == 1) {
            // Cập nhật trạng thái vé thành CONFIRMED
            b.setStatus("CONFIRMED");

            // Cập nhật thông tin thanh toán
            Ticket.PaymentInfo p = b.getPayment();
            p.setPaidAt(Instant.now()); // Lưu thời điểm thanh toán
            p.setTxId(String.valueOf(parsed.get("zp_trans_id"))); // Lưu mã giao dịch ZaloPay
            p.setRaw(parsed); // Lưu toàn bộ dữ liệu thô từ ZaloPay

            Object zptObj = parsed.get("zp_trans_id");
            if (zptObj != null) {
                Ticket.PaymentInfo pay = b.getPayment();
                if (pay == null) {
                    pay = new Ticket.PaymentInfo();
                    pay.setGateway("ZALOPAY");
                }
                try { pay.setTxId(String.valueOf(zptObj)); } catch (Throwable ignore) {}
                try { pay.setZpTransId(String.valueOf(zptObj)); } catch (Throwable ignore) {}
                b.setPayment(pay);
            }

            // Lưu vé đã cập nhật vào cơ sở dữ liệu
            ticketRepo.save(b);

            Logger log = LoggerFactory.getLogger(getClass());
            log.debug("IPN confirm: bookingId={}, holdId={}, showtimeId={}, seats={}",
                    b.getId(), b.getHoldId(), b.getShowtimeId(), b.getSeats());

            // 15. Cập nhật ledger (sổ cái) để xác nhận ghế ngồi cho suất chiếu
            long updated = ledgerRepo.confirmMany(b.getShowtimeId(), b.getSeats(), b.getId(), b.getHoldId());

            // 16. Kiểm tra số lượng ghế được cập nhật có khớp với số ghế của vé hay không
            if (updated != b.getSeats().size()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "SEAT_TAKEN_OR_HOLD_MISMATCH");
            }

            // 16.1. Xoá SeatLock sau khi xác nhận thành công (tránh lock còn treo)
            try {
                lockRepo.deleteById(b.getHoldId());
            } catch (Throwable ignore) {}

        } else {
            // 17. Trường hợp thanh toán thất bại (status != 1)
            // cập nhật trạng thái vé thành CANCELED
            b.setStatus("CANCELED");
            ticketRepo.save(b);
            // Giải phóng ghế trong ledger (nếu cần)
            //ledgerRepo.releaseSeats(b.getShowtimeId(), b.getSeats(), b.getHoldId());
        }
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
