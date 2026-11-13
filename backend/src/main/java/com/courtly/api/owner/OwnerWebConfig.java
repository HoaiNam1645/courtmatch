package com.courtly.api.owner;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Gan {@link OwnerAccessInterceptor} cho khu quan ly cua chu san. */
@Configuration
@RequiredArgsConstructor
public class OwnerWebConfig implements WebMvcConfigurer {

    /** Doi mat khau tam phai di qua duoc thi moi go duoc co bat buoc doi mat khau. */
    static final String CHANGE_PASSWORD_PATH = "/api/v1/owner/account/password";

    private final OwnerAccessInterceptor ownerAccessInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ownerAccessInterceptor)
                .addPathPatterns("/api/v1/owner/**")
                .excludePathPatterns(CHANGE_PASSWORD_PATH);
    }
}
