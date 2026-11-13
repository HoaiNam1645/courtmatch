package com.courtly.api.owner;

import com.courtly.service.owner.OwnerAccountService;
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
 * Cua vao chung cua moi API {@code /api/v1/owner/**} (2.3.1).
 *
 * <p>SecurityConfig da chan theo vai tro trong token. Lop nay kiem tra them trang thai hien
 * tai trong database, de khong controller nao cua chu san quen kiem tra.
 */
@Component
@RequiredArgsConstructor
public class OwnerAccessInterceptor implements HandlerInterceptor {

    private final OwnerAccountService ownerAccountService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            ownerAccountService.requireUsableOwner(UUID.fromString(jwt.getSubject()));
        }
        return true;
    }
}
