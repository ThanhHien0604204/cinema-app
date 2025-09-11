package com.ntth.spring_boot_heroku_cinema_app.pojo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "idempotency_records")
@CompoundIndex(name = "uniq_user_key", def = "{'userId': 1, 'key': 1}", unique = true)
public class IdempotencyRecord {
    @Id
    private String id;

    private String userId;
    private String key;
    private String bookingId; // kết quả cuối cùng cho key

    // getters/setters...
}
