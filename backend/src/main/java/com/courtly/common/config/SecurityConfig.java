package com.courtly.common.config;

import com.courtly.common.exception.ApiError;
import com.courtly.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Cau hinh xac thuc: stateless, xac minh JWT o moi request. */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class, SepayProperties.class,
        MailProperties.class, PasswordResetProperties.class, EmailVerificationProperties.class})
public class SecurityConfig {

    private static final String[] PUBLIC_POST_ENDPOINTS = {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            // 2.1.1 - xac minh email: tai khoan chua kich hoat nen chua co token
            "/api/v1/auth/verify-email",
            "/api/v1/auth/verify-email/resend",
            // 2.1.5 - nguoi quen mat khau thi chua dang nhap duoc
            "/api/v1/auth/password-reset",
            "/api/v1/auth/password-reset/verify",
            "/api/v1/auth/password-reset/confirm",
            // Xem gia truoc khi dang nhap; chi buoc tao don that moi can dang nhap (2.1.27).
            "/api/v1/bookings/quote",
            // SePay goi tu Internet, khong co JWT. Tu bao ve bang khoa rieng trong controller.
            "/api/v1/payments/sepay/webhook"
    };

    /** Duyet san khong can dang nhap (2.1.13 - 2.1.18). */
    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/api/v1/venues",
            "/api/v1/venues/**",
            "/actuator/health"
    };

    private final JwtProperties jwtProperties;
    private final CorsProperties corsProperties;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtProperties jwtProperties, CorsProperties corsProperties, ObjectMapper objectMapper) {
        this.jwtProperties = jwtProperties;
        this.corsProperties = corsProperties;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // API khong dung cookie phien nen khong can CSRF token.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        // Loi xac thuc khong di qua @RestControllerAdvice nen phai tra
                        // dung hinh dang ApiError o day.
                        .authenticationEntryPoint((request, response, exception) -> writeError(
                                response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED,
                                "Ban can dang nhap de thuc hien thao tac nay."))
                        .accessDeniedHandler((request, response, exception) -> writeError(
                                response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN,
                                "Ban khong co quyen thuc hien thao tac nay.")))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, exception) -> writeError(
                                response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED,
                                "Ban can dang nhap de thuc hien thao tac nay.")))
                .build();
    }

    private void writeError(HttpServletResponse response, int status, String code, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiError.of(code, message, Map.of()));
    }

    /** Doi claim "roles" thanh authority dang ROLE_player, ROLE_owner... */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private SecretKeySpec secretKey() {
        byte[] keyBytes = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "courtly.security.jwt.secret phai dai it nhat 32 ky tu de dung HS256.");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
