package com.courtly.service.ai;

import com.courtly.common.config.AiProperties;
import com.courtly.common.enums.ModelType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Goi REST noi bo toi AI service (2.2.17): xac thuc bang X-Internal-Token, timeout ngan, KHONG thu lai trong cung yeu cau cua nguoi choi.
 * Moi loi deu doi thanh {@link AiUnavailableException} de noi goi dung baseline.
 */
@Component
public class AiServiceClient {

    public static final int MAX_CANDIDATES = 200;

    private final AiProperties properties;
    private final RestClient client;

    public AiServiceClient(AiProperties properties) {
        this.properties = properties;
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(Math.max(1, properties.connectTimeoutMs()))).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(Math.max(1, properties.timeoutMs())));
        this.client = RestClient.builder().baseUrl(properties.baseUrl() == null ? "http://127.0.0.1:8001" : properties.baseUrl())
                .requestFactory(factory).build();
    }

    public boolean usable() {
        return properties.usable();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Reason(String code, BigDecimal weight) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ranked(String userId, BigDecimal totalScore, BigDecimal confidence, List<Reason> reasons) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RankResponse(String modelVersion, String scoringSource, List<Ranked> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Balanced(String optionId, BigDecimal team1WinProbability, BigDecimal balance) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BalanceResponse(String modelVersion, List<Balanced> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Loaded(String modelVersion, String checksum, String kind) {
    }

    public record RankCandidate(String userId, Map<String, Object> features) {
    }

    public record BalanceOption(String optionId, Map<String, Object> features) {
    }

    public RankResponse rankPartners(String requesterId, List<RankCandidate> candidates) {
        if (candidates.size() > MAX_CANDIDATES) {
            throw new AiUnavailableException("too_many_candidates", "Qua nhieu ung vien cho mot lan goi AI");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requesterId", requesterId);
        body.put("candidates", candidates);
        return post("/v1/rank-partners", body, RankResponse.class);
    }

    public BalanceResponse predictBalance(List<BalanceOption> options) {
        return post("/v1/predict-balance", Map.of("options", options), BalanceResponse.class);
    }

    public Loaded loadModel(ModelType type, String version, String checksum) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("modelType", type.getValue());
        body.put("modelVersion", version);
        body.put("checksum", checksum);
        return post("/v1/models/load", body, Loaded.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> health() {
        try {
            return client.get().uri("/health").retrieve().body(Map.class);
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    private <T> T post(String path, Object body, Class<T> type) {
        if (!usable()) {
            throw new AiUnavailableException("ai_disabled", "AI service chua duoc bat hoac chua cau hinh token");
        }
        try {
            T result = client.post().uri(path).contentType(MediaType.APPLICATION_JSON).header("X-Internal-Token", properties.token())
                    .body(body).retrieve().body(type);
            if (result == null) {
                throw new AiUnavailableException("empty_response", "AI service tra ve noi dung rong");
            }
            return result;
        } catch (AiUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    private static AiUnavailableException translate(RuntimeException exception) {
        if (exception instanceof RestClientResponseException response) {
            return new AiUnavailableException("http_" + response.getStatusCode().value(),
                    "AI service tra ve " + response.getStatusCode().value(), exception);
        }
        if (exception instanceof ResourceAccessException access) {
            Throwable cause = access.getCause();
            boolean timeout = cause instanceof java.net.http.HttpTimeoutException || cause instanceof TimeoutException
                    || cause instanceof java.net.SocketTimeoutException;
            return new AiUnavailableException(timeout ? "timeout" : "connection", "Khong goi duoc AI service", exception);
        }
        return new AiUnavailableException("error", "Loi khi goi AI service", exception);
    }
}
