package com.courtly.service.auth;

import com.courtly.common.config.JwtProperties;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRole;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Sinh access token cho nguoi dung vua dang nhap hoac vua dang ky. */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    /** @return chuoi JWT da ky, subject la user id */
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        List<String> roles = user.getUserRoles().stream()
                .map(UserRole::getRole)
                .map(role -> role.getCode())
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(properties.expirationMinutes(), ChronoUnit.MINUTES))
                .subject(user.getId().toString())
                .claim("roles", roles)
                .build();

        // Phai chi dinh HS256: mac dinh encoder chon RS256 va khong khop khoa HMAC.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long expiresInSeconds() {
        return properties.expirationMinutes() * 60;
    }
}
