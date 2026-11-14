package com.courtly.api.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Gan cac interceptor kiem tra tai khoan (2.3.28) va khu quan tri. */
@Configuration
@RequiredArgsConstructor
public class AdminWebConfig implements WebMvcConfigurer {

    private final AdminAccessInterceptor adminAccessInterceptor;
    private final ActiveAccountInterceptor activeAccountInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Cac endpoint cong khai tu dang nhap / dang ky / quen mat khau khong can token nen khong kiem tra.
        registry.addInterceptor(activeAccountInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/verify-email",
                        "/api/v1/auth/verify-email/resend", "/api/v1/auth/password-reset",
                        "/api/v1/auth/password-reset/verify", "/api/v1/auth/password-reset/confirm",
                        "/api/v1/bookings/quote", "/api/v1/media/**");
        registry.addInterceptor(adminAccessInterceptor).addPathPatterns("/api/v1/admin/**");
    }
}
