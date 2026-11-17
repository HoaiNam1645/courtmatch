package com.courtly.service.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class MatchingRunRecorderTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final MatchingRunRecorder recorder = new MatchingRunRecorder(jdbc, null, new ObjectMapper());

    @Test
    @DisplayName("Lan that bai ghi trang thai failed, thong diep bi cat con 300 ky tu")
    void recordsFailureWithTruncatedMessage() {
        recorder.recordFailure(MatchingRunRecorder.PARTNER_SEARCH, UUID.randomUUID(), 12,
                new IllegalStateException("x".repeat(1000)), null);
        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(anyString(), args.capture(), args.capture(), args.capture(), args.capture(),
                args.capture(), args.capture(), args.capture(), args.capture(), args.capture(), args.capture());
        assertThat(args.getAllValues()).contains("failed");
        String message = (String) args.getAllValues().stream()
                .filter(v -> v instanceof String s && s.startsWith("IllegalStateException")).findFirst().orElseThrow();
        assertThat(message).hasSize(300);
    }

    @Test
    @DisplayName("Loi khi ghi nhat ky that bai khong lam mat loi goc")
    void failureLoggingNeverThrows() {
        when(jdbc.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("db down"));
        recorder.recordFailure(MatchingRunRecorder.DOUBLE_TEAM, UUID.randomUUID(), 1, new RuntimeException("boom"), null);
    }

    @Test
    @DisplayName("Diem tong baseline la trung binh co trong so, thanh phan thieu duoc bo va chuan hoa lai")
    void totalScoreWeights() {
        BigDecimal all = PartnerScoring.total(new BigDecimal("100"), new BigDecimal("50"),
                new BigDecimal("0"), new BigDecimal("50"));
        assertThat(all).isEqualByComparingTo("60.00");
        BigDecimal withoutTime = PartnerScoring.total(new BigDecimal("100"), new BigDecimal("50"), null, new BigDecimal("50"));
        assertThat(withoutTime).isEqualByComparingTo("75.00");
        assertThat(PartnerScoring.total(null, null, null, null)).isEqualByComparingTo("0");
    }
}
