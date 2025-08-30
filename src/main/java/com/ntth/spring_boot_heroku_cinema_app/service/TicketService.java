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
     * @param paymentMethod "CASH" | "VNPAY"
     */
    /**
     * Tạo booking từ hold: trạng thái PENDING_PAYMENT (ZaloPay)
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

        // 2) Tạo booking code
        String bookingCode = genCode(); // ví dụ UIN-XXXXXX

        // 3) Lập booking ban đầu
        Ticket b = new Ticket();
        b.setBookingCode(bookingCode);
        b.setUserId(userId);
        b.setShowtimeId(hold.getShowtimeId());
        b.setSeats(new ArrayList<>(hold.getSeats()));
        b.setAmount(hold.getAmount());
        b.setHoldId(holdId);
        b.setStatus("PENDING_PAYMENT");
        b.setCreatedAt(Instant.now());
        Ticket.PaymentInfo p = new Ticket.PaymentInfo();
        p.setGateway("ZALOPAY");
//        if ("CASH".equalsIgnoreCase(paymentMethod)) {
//            p.setGateway("CASH");
//            b.setStatus("CONFIRMED"); // xác nhận ngay
//        } else {
//            p.setGateway(paymentMethod.toUpperCase(Locale.ROOT));
//            b.setStatus("PENDING_PAYMENT");
//        }
        b.setPayment(p);

        // 4) Lưu booking
        ticketRepo.save(b);
//        // 5) Nếu CASH: chuyển ledger từ HOLD -> CONFIRMED cho tất cả ghế
//        if ("CONFIRMED".equals(b.getStatus())) {
//            long updated = ledgerRepo.confirmMany(hold.getShowtimeId(), hold.getSeats(), b.getId(), holdId);
//            if (updated != hold.getSeats().size()) {
//                // Không chuyển được đủ ghế ⇒ rollback transaction
//                throw new ResponseStatusException(HttpStatus.CONFLICT, "SEAT_TAKEN_OR_HOLD_MISMATCH");
//            }
//        }

        return b;
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
     Tham số:
     - req: Map chứa dữ liệu IPN từ ZaloPay, bao gồm "data" và "mac" để xác minh
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

            // Lưu vé đã cập nhật vào cơ sở dữ liệu
            ticketRepo.save(b);

            // 15. Cập nhật ledger (sổ cái) để xác nhận ghế ngồi cho suất chiếu
            long updated = ledgerRepo.confirmMany(b.getShowtimeId(), b.getSeats(), b.getId(), b.getHoldId());

            // 16. Kiểm tra số lượng ghế được cập nhật có khớp với số ghế của vé hay không
            if (updated != b.getSeats().size()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "SEAT_TAKEN_OR_HOLD_MISMATCH");
            }
        } else {
            // 17. Trường hợp thanh toán thất bại (status != 1)
            // cập nhật trạng thái vé thành CANCELED
            //log.warn("Payment failed for bookingCode: {}, status: {}", bookingCode, status);
            b.setStatus("CANCELED");
            ticketRepo.save(b);
            // Giải phóng ghế trong ledger (nếu cần)
            //ledgerRepo.releaseSeats(b.getShowtimeId(), b.getSeats(), b.getHoldId());
        }
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
        // 1. Tìm vé trong cơ sở dữ liệu dựa trên bookingId
        Ticket b = ticketRepo.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));

        // 2. Kiểm tra trạng thái vé, chỉ cho phép hủy nếu vé đang ở trạng thái CONFIRMED
        if (!"CONFIRMED".equals(b.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ONLY_CONFIRMED_CAN_BE_REFUNDED");
        }

        // 3. Kiểm tra xem vé có sử dụng cổng thanh toán ZaloPay hay không
        // Nếu không phải ZaloPay, ném lỗi BAD_REQUEST
        if (b.getPayment() == null ||!"ZALOPAY".equalsIgnoreCase(b.getPayment().getGateway())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NOT_ZALOPAY_BOOKING");
        }

        // 4. Lấy mã giao dịch ZaloPay (zp_trans_id) từ thông tin thanh toán của vé
        String zpTransId = b.getPayment().getTxId();
        if (zpTransId == null || zpTransId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "MISSING_ZP_TRANS_ID: " + bookingId);
        }
        // 4.5. Vệ sinh lý do hủy
        String refundReason = (reason == null || reason.trim().isEmpty()) ? "refund" : reason.trim();
        if (refundReason.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_REFUND_REASON");
        }

        // 5. Gọi API hoàn tiền (refund) của ZaloPay
        // Sử dụng appId, key1 từ cấu hình ZaloPay, cùng với mã giao dịch, số tiền và lý do hủy
        Map<String, Object> ret = zalo.refund(zalo.getAppId(), zalo.getKey1(), zpTransId, b.getAmount(),
                reason == null ? "refund" : reason);

        // 6. Kiểm tra mã trả về từ API refund, nếu không phải 1 (thành công) thì ném lỗi
        log.info("Processing refund for bookingId: {}, zpTransId: {}", bookingId, zpTransId);
        int rc = ((Number) ret.getOrDefault("return_code", -1)).intValue();
        if (rc != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ZALO_REFUND_FAILED: " + ret);
        }

        // 7. Cập nhật trạng thái vé thành REFUNDED
        b.setStatus("REFUNDED");
        ticketRepo.save(b);
        log.info("Refund successful, updated bookingId: {} to REFUNDED", bookingId);

        // 8. Trả ghế về trạng thái FREE (ngay lập tức)
        long freed = ledgerRepo.freeMany(b.getShowtimeId(), b.getSeats(), b.getId());

        // 9. Trả về đối tượng vé đã được cập nhật
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
