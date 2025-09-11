package com.ntth.spring_boot_heroku_cinema_app.pojo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

    @Document("seat_locks")
    public class SeatLock {
        @Id
        private String id;         // holdId
        private String userId;
        private String showtimeId;
        private List<String> seats;
        private long Amount;           // VND

        // TTL: Mongo sẽ tự xoá document sau khi qua expiresAt
        @Indexed(expireAfterSeconds = 0)
        private Instant expiresAt;     // TTL

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
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
            return Amount;
        }

        public void setAmount(long amount) {
            Amount = amount;
        }

        public Instant getExpiresAt() {
            return expiresAt;
        }

        public void setExpiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }
    }
