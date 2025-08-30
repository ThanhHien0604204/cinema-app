//package com.ntth.spring_boot_heroku_cinema_app.controller;
//
//import com.ntth.spring_boot_heroku_cinema_app.filter.JwtUser;
//import com.ntth.spring_boot_heroku_cinema_app.pojo.Ticket;
//import com.ntth.spring_boot_heroku_cinema_app.service.TicketService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.core.annotation.AuthenticationPrincipal;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.util.Map;
//
//@RestController
//@RequestMapping("/api/bookings")
//public class TicketController {
//    @Autowired
//    private TicketService ticketService;
//    @PostMapping
//    public ResponseEntity<?> createBooking(@RequestBody Map<String,String> body,
//                                           @AuthenticationPrincipal JwtUser principal) {
//        String holdId = body.get("holdId");
//        String method = body.getOrDefault("paymentMethod", "VNPAY");
//        Ticket b = ticketService.createBookingFromHold(holdId, principal.getUserId(), method);
//        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
//                "bookingId", b.getId(),
//                "bookingCode", b.getBookingCode(),
//                "status", b.getStatus(),
//                "amount", b.getAmount()
//        ));
//    }
//}
