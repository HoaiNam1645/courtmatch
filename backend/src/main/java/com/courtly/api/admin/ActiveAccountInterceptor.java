package com.courtly.api.admin;

import com.courtly.common.enums.UserStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Tai khoan bi khoa hoac ngung sau khi da dang nhap thi token cu khong con dung duoc (2.3.28).
 *
 * <p>Duyet san cong khai (GET /api/v1/venues/**) van mo cho moi nguoi, ke ca khi trinh duyet con giu token cu.
 */
@Component
@RequiredArgsConstructor
public class ActiveAccountInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("GET".equals(request.getMethod()) && request.getRequestURI().startsWith("/api/v1/venues")) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            UserStatus status = userRepository.findStatusById(UUID.fromString(jwt.getSubject())).orElse(null);
            if (status != UserStatus.ACTIVE) {
                throw ApiException.forbidden(ErrorCode.ACCOUNT_DISABLED, "Tai khoan dang bi khoa.");
            }
        }
        return true;
    }
}
