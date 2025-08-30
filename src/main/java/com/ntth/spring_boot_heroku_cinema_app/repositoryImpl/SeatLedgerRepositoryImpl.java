package com.ntth.spring_boot_heroku_cinema_app.repositoryImpl;

import com.mongodb.client.result.UpdateResult;
import com.ntth.spring_boot_heroku_cinema_app.pojo.SeatLedger;
import com.ntth.spring_boot_heroku_cinema_app.pojo.SeatState;
import com.ntth.spring_boot_heroku_cinema_app.repository.SeatLedgerRepositoryCustom;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SeatLedgerRepositoryImpl implements SeatLedgerRepositoryCustom {

    private final MongoTemplate mongo;

    public SeatLedgerRepositoryImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public boolean confirm(String showtimeId, String seat, String bookingId, String holdId) {
        String id = showtimeId + "#" + seat;
        Query q = new Query();
        q.addCriteria(Criteria.where("_id").is(id));
        q.addCriteria(Criteria.where("state").is(SeatState.HOLD.name()));
        q.addCriteria(Criteria.where("refType").is("LOCK"));
        q.addCriteria(Criteria.where("refId").is(holdId));

        Update u = new Update()
                .set("state", SeatState.CONFIRMED.name())
                .set("refType", "BOOKING")
                .set("refId", bookingId)
                .unset("expiresAt");

        UpdateResult r = mongo.updateFirst(q, u, SeatLedger.class);
        return r.getModifiedCount() == 1;
    }

    @Override
    public long confirmMany(String showtimeId, List<String> seats, String bookingId, String holdId) {
        List<String> ids = seats.stream().map(s -> showtimeId + "#" + s).toList();

        Query q = new Query();
        q.addCriteria(Criteria.where("_id").in(ids));
        q.addCriteria(Criteria.where("state").is(SeatState.HOLD.name()));
        q.addCriteria(Criteria.where("refType").is("LOCK"));
        q.addCriteria(Criteria.where("refId").is(holdId));

        Update u = new Update()
                .set("state", SeatState.CONFIRMED.name())
                .set("refType", "BOOKING")
                .set("refId", bookingId)
                .unset("expiresAt");

        UpdateResult r = mongo.updateMulti(q, u, SeatLedger.class);
        return r.getModifiedCount();
    }
    @Override
    public long freeMany(String showtimeId, List<String> seats, String bookingId) {
        // Chỉ trả FREE những ghế hiện đang CONFIRMED bởi bookingId này
        // (idempotent: nếu ghế đã FREE hoặc thuộc booking khác -> không update)
        List<String> ids = seats.stream()
                .map(s -> showtimeId + "#" + s)
                .toList();

        Query q = new Query();
        q.addCriteria(Criteria.where("_id").in(ids));
        q.addCriteria(Criteria.where("state").is(SeatState.CONFIRMED.name()));
        q.addCriteria(Criteria.where("refType").is("BOOKING"));
        q.addCriteria(Criteria.where("refId").is(bookingId));

        Update u = new Update()
                .set("state", SeatState.FREE.name())
                .unset("refType")
                .unset("refId")
                .unset("expiresAt"); // FREE thì không giữ TTL

        UpdateResult r = mongo.updateMulti(q, u, SeatLedger.class);
        return r.getModifiedCount(); // số ghế được trả về FREE
    }
}
