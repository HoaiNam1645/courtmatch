package com.courtly.api.admin;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Lay id quan tri vien tu token; id luon tu token, khong nhan tu request. */
final class AdminJwt {

    private AdminJwt() {
    }

    static UUID adminId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
