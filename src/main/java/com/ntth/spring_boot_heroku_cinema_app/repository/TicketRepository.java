package com.ntth.spring_boot_heroku_cinema_app.repository;

import com.ntth.spring_boot_heroku_cinema_app.pojo.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends MongoRepository<Ticket, String> {

    // Query nested PaymentInfo (ví dụ filter gateway VNPAY)
    @Query("{ 'userId': ?0, 'status': ?1 }")  // Query cơ bản theo userId và status
    Page<Ticket> findByUserIdAndStatus(String userId, String status, Pageable pageable);

    // Nested query ví dụ: Filter theo payment.gateway
    @Query("{ 'userId': ?0, 'status': ?1, 'payment.gateway': ?2 }")  // Nested field
    List<Ticket> findByUserIdAndStatusAndPaymentGateway(String userId, String status, String gateway);

    // Tự động suy luận query theo tên hàm
    Optional<Ticket> findByBookingCode(String bookingCode);

    // Nếu cần tìm theo user + id để bảo toàn quyền truy cập
    Optional<Ticket> findByIdAndUserId(String id, String userId);

    List<Ticket> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Ticket> findByUserIdAndShowtimeIdInOrderByCreatedAtDesc(String userId, Collection<String> showtimeIds);

}
