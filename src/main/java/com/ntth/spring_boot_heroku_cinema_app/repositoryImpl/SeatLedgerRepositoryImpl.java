package com.ntth.spring_boot_heroku_cinema_app.repositoryImpl;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.result.UpdateResult;
import com.ntth.spring_boot_heroku_cinema_app.pojo.SeatLedger;
import com.ntth.spring_boot_heroku_cinema_app.pojo.SeatState;
import com.ntth.spring_boot_heroku_cinema_app.repository.SeatLedgerRepositoryCustom;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class SeatLedgerRepositoryImpl implements SeatLedgerRepositoryCustom {

    private final MongoTemplate mongo;

    public SeatLedgerRepositoryImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** FREE -> HOLD (lock theo holdId, set TTL) */
    @Override
    public long holdSeats(String showtimeId, List<String> seats, String holdId, Instant expiresAt) {
        Query q = new Query();
        q.addCriteria(Criteria.where("showtimeId").is(showtimeId));
        q.addCriteria(Criteria.where("seat").in(seats));
        q.addCriteria(Criteria.where("status").is(SeatState.FREE.name()));

        Update u = new Update()
                .set("status", SeatState.HOLD.name())
                .set("refType", "LOCK")
                .set("refId", holdId)
                .set("expiresAt", expiresAt);

        UpdateResult r = mongo.updateMulti(q, u, SeatLedger.class);
        return r.getModifiedCount();
    }

    /** HOLD (đúng holdId, chưa hết hạn) -> CONFIRMED (gắn bookingId) */
    @Override
    public long confirmSeatsByHold(String showtimeId, List<String> seats, String holdId, String bookingId) {
        Query q = new Query();
        q.addCriteria(Criteria.where("showtimeId").is(showtimeId));
        q.addCriteria(Criteria.where("seat").in(seats));
        q.addCriteria(Criteria.where("status").is(SeatState.HOLD.name()));
        q.addCriteria(Criteria.where("refType").is("LOCK"));
        q.addCriteria(Criteria.where("refId").is(holdId));
        q.addCriteria(Criteria.where("expiresAt").gt(Instant.now()));

        Update u = new Update()
                .set("status", SeatState.CONFIRMED.name())
                .set("refType", "BOOKING")
                .set("refId", bookingId)
                .unset("expiresAt");

        UpdateResult r = mongo.updateMulti(q, u, SeatLedger.class);
        return r.getModifiedCount();
    }

    /** HOLD (đúng holdId) -> FREE (rollback hold) */
    @Override
    public long releaseSeatsByHold(String showtimeId, List<String> seats, String holdId) {
        Query q = new Query();
        q.addCriteria(Criteria.where("showtimeId").is(showtimeId));
        q.addCriteria(Criteria.where("seat").in(seats));
        q.addCriteria(Criteria.where("status").is(SeatState.HOLD.name()));
        q.addCriteria(Criteria.where("refType").is("LOCK"));
        q.addCriteria(Criteria.where("refId").is(holdId));

        Update u = new Update()
                .set("status", SeatState.FREE.name())
                .unset("refType")
                .unset("refId")
                .unset("expiresAt");

        UpdateResult r = mongo.updateMulti(q, u, SeatLedger.class);
        return r.getModifiedCount();
    }

    /** Giải phóng mọi HOLD đã hết hạn -> FREE */
    @Override
    public long releaseExpiredLocks(Instant now) {
        Query q = new Query();
        q.addCriteria(Criteria.where("status").is(SeatState.HOLD.name()));
        q.addCriteria(Criteria.where("refType").is("LOCK"));
        q.addCriteria(Criteria.where("expiresAt").lt(now));

        Update u = new Update()
                .set("status", SeatState.FREE.name())
                .unset("refType")
                .unset("refId")
                .unset("expiresAt");

        UpdateResult r = mongo.updateMulti(q, u, SeatLedger.class);
        return r.getModifiedCount();
    }

    /** CONFIRMED (đúng bookingId) -> FREE (khi huỷ) */
    @Override
    public long releaseSeatsByBookingId(String bookingId) {
        Query q = new Query();
        q.addCriteria(Criteria.where("status").is(SeatState.CONFIRMED.name()));
        q.addCriteria(Criteria.where("refType").is("BOOKING"));
        q.addCriteria(Criteria.where("refId").is(bookingId));

        Update u = new Update()
                .set("status", SeatState.FREE.name())
                .unset("refType")
                .unset("refId")
                .unset("expiresAt");

        UpdateResult r = mongo.updateMulti(q, u, SeatLedger.class);
        return r.getModifiedCount();
    }

    /** Giữ nguyên API tiện dụng đã khai báo trong interface */
    @Override
    public long confirmMany(String showtimeId, List<String> seats, String bookingId, String holdId) {
        if (seats == null || seats.isEmpty()) return 0;
        // Chuyển từ HOLD -> CONFIRMED
        Query q = new Query();
        q.addCriteria(Criteria.where("showtimeId").is(showtimeId));
        q.addCriteria(Criteria.where("seatNumber").in(seats));
        q.addCriteria(Criteria.where("status").is("HOLD"));
        q.addCriteria(Criteria.where("refId").is(holdId));        // <— BẮT BUỘC
        q.addCriteria(Criteria.where("refType").is("LOCK"));

        Update u = new Update()
                .set("status", "CONFIRMED")
                .set("refType", "BOOKING")
                .set("refId", bookingId)
                .unset("expiresAt");

        return mongo.updateMulti(q, u, SeatLedger.class).getModifiedCount();
    }


    @Override
    public long freeMany(String showtimeId, List<String> seats, String bookingId) {
        // Trả về FREE chỉ những ghế CONFIRMED của booking này (idempotent-safe)
        Query q = new Query();
        q.addCriteria(Criteria.where("showtimeId").is(showtimeId));
        q.addCriteria(Criteria.where("seat").in(seats));
        q.addCriteria(Criteria.where("status").is(SeatState.CONFIRMED.name()));
        q.addCriteria(Criteria.where("refType").is("BOOKING"));
        q.addCriteria(Criteria.where("refId").is(bookingId));

        Update u = new Update()
                .set("status", SeatState.FREE.name())
                .unset("refType")
                .unset("refId")
                .unset("expiresAt");

        UpdateResult r = mongo.updateMulti(q, u, SeatLedger.class);
        return r.getModifiedCount();
    }
    @Override
    public long lockFromFree(String showtimeId, List<String> seats, String holdId, Instant expiresAt) {
        Instant now = Instant.now();
        BulkOperations ops = mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, SeatLedger.class);

        for (String seat : seats) {
            // Cho phép lấy ghế nếu đang FREE, hoặc chưa có doc,
            // hoặc đang HOLD nhưng đã hết hạn.
            Criteria canLock = new Criteria().orOperator(
                    Criteria.where("status").is(SeatState.FREE),
                    Criteria.where("status").exists(false),
                    new Criteria().andOperator(
                            Criteria.where("status").is(SeatState.HOLD),
                            new Criteria().orOperator(
                                    Criteria.where("expiresAt").lt(now),
                                    Criteria.where("expiresAt").is(null)
                            )
                    )
            );

            Query q = new Query(new Criteria().andOperator(
                    Criteria.where("showtimeId").is(showtimeId),
                    Criteria.where("seat").is(seat),
                    canLock
            ));

            Update u = new Update()
                    .set("showtimeId", showtimeId)
                    .set("seat", seat)
                    .set("status", SeatState.HOLD)
                    .set("refType", "LOCK")
                    .set("refId", holdId)
                    .set("expiresAt", expiresAt);

            ops.upsert(q, u); // <- quan trọng: tạo doc nếu chưa có
        }

        BulkWriteResult r = ops.execute();
        return r.getModifiedCount() + r.getUpserts().size();
    }
}