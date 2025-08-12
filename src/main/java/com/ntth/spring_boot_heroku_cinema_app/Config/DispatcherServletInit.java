/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ntth.spring_boot_heroku_cinema_app.Config;

import jakarta.servlet.Filter;
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletRegistration;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

/**
 * @author admin
 */
public class DispatcherServletInit extends AbstractAnnotationConfigDispatcherServletInitializer {

    @Override//k kế thừa ai thì làm trong đây
    //Chứa bean toàn cục như Thymeleaf, Hibernate, bảo mật
    protected Class<?>[] getRootConfigClasses() {
        return new Class[]{
                //rổ dậu
                SpringSecurityConfigs.class
        };
    }

    @Override//Cấu hình riêng cho DispatcherServlet, thường là Web MVC
    protected Class<?>[] getServletConfigClasses() {
        return new Class[]{
                WebAppContextConfigs.class
        };
    }

    @Override
    protected String[] getServletMappings() {//chỉ ddingj kí hiệu để ánh xạ
        return new String[]{"/"}; // Định nghĩa servlet này xử lý toàn bộ (/) các request
    }

    @Override
    ////cấu hình upload file đa phần (multipart).
    protected void customizeRegistration(ServletRegistration.Dynamic registration) {
        String location = "/";
        long maxFileSize = 5242880; // 5MB
        long maxRequestSize = 20971520; // 20MB
        int fileSizeThreshold = 0;

        registration.setMultipartConfig(new MultipartConfigElement(location, maxFileSize, maxRequestSize, fileSizeThreshold));
    }
//    
//    @Override////thêm JwtFilter, hiện tại đang bị comment.
//    protected Filter[] getServletFilters() {
//        return new Filter[] { new JwtFilter() }; // Filter sẽ áp dụng cho mọi request
//    }
}
