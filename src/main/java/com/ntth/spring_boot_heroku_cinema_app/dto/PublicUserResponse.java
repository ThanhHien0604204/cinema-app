package com.ntth.spring_boot_heroku_cinema_app.dto;

import com.ntth.spring_boot_heroku_cinema_app.pojo.User;

//Tạo DTO trả về an toàn
public class PublicUserResponse {
    public String id;
    public String userName;
    public String email;      // nếu không muốn lộ email, có thể bỏ field này
//    public String avatarUrl;  // nếu chưa có, tạm để null/placeholder

    public static PublicUserResponse of(User u) {
        PublicUserResponse dto = new PublicUserResponse();
        dto.id = u.getId();
        dto.userName = u.getUserName();
        dto.email = u.getEmail();
//        dto.avatarUrl = null; // hoặc lấy từ u.getAvatarUrl() nếu bạn có thuộc tính này
        return dto;
    }
}
