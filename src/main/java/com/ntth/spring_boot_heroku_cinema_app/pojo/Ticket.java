package com.ntth.spring_boot_heroku_cinema_app.pojo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Document("ticket")
public class Ticket {
    @Id
    private String id;
    @Indexed(unique = true)
    private String bookingCode;    // dùng làm vnp_TxnRef
    private String userId;
    private String showtimeId;
    private List<String> seats;
    private long amount;
    private String status;         // PENDING_PAYMENT|CONFIRMED|CANCELED|REFUNDED
    @Indexed(unique = true)
    private String holdId;

    private PaymentInfo payment;   // simple inner class
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBookingCode() {
        return bookingCode;
    }

    public void setBookingCode(String bookingCode) {
        this.bookingCode = bookingCode;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getShowtimeId() {
        return showtimeId;
    }

    public void setShowtimeId(String showtimeId) {
        this.showtimeId = showtimeId;
    }

    public List<String> getSeats() {
        return seats;
    }

    public void setSeats(List<String> seats) {
        this.seats = seats;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getHoldId() { return holdId; }

    public void setHoldId(String holdId) { this.holdId = holdId; }

    public PaymentInfo getPayment() {
        return payment;
    }

    public void setPayment(PaymentInfo payment) {
        this.payment = payment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public static class PaymentInfo {
        private String gateway;      // VNPAY|CASH|...
        private String intentId;
        private Instant paidAt;
        private String txId;
        private Map<String, Object> raw;
        private String zpTransId;

        public String getGateway() {
            return gateway;
        }

        public void setGateway(String gateway) {
            this.gateway = gateway;
        }

        public String getIntentId() {
            return intentId;
        }

        public void setIntentId(String intentId) {
            this.intentId = intentId;
        }

        public Instant getPaidAt() {
            return paidAt;
        }

        public void setPaidAt(Instant paidAt) {
            this.paidAt = paidAt;
        }

        public String getTxId() {
            return txId;
        }

        public void setTxId(String txId) {
            this.txId = txId;
        }

        public Map<String, Object> getRaw() {
            return raw;
        }

        public void setRaw(Map<String, Object> raw) {
            this.raw = raw;
        }

        public String getZpTransId() {
            return zpTransId;
        }

        public void setZpTransId(String zpTransId) {
            this.zpTransId = zpTransId;
        }
    }
}
