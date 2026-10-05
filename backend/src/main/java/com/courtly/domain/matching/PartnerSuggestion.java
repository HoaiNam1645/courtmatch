package com.courtly.domain.matching;

import com.courtly.common.BaseEntity;
import com.courtly.common.enums.SuggestionStatus;
import com.courtly.domain.account.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Mot doi tac duoc de xuat cho nguoi choi, kem diem thanh phan (2.2.29). */
@Entity
@Table(name = "partner_suggestions")
@Getter
@Setter
@NoArgsConstructor
public class PartnerSuggestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id")
    private AlgorithmRun run;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "suggested_user_id", nullable = false)
    private User suggestedUser;

    /** Diem tuong dong ky nang (2.2.24). */
    @Column(name = "skill_score", precision = 6, scale = 2)
    private BigDecimal skillScore;

    /** Diem tuong thich vi tri (2.2.25). */
    @Column(name = "location_score", precision = 6, scale = 2)
    private BigDecimal locationScore;

    /** Diem tuong thich thoi gian (2.2.26). */
    @Column(name = "time_score", precision = 6, scale = 2)
    private BigDecimal timeScore;

    /** Diem tuong thich phong cach choi (2.2.27). */
    @Column(name = "style_score", precision = 6, scale = 2)
    private BigDecimal styleScore;

    /** Diem tong the dung de xep hang (2.2.28). */
    @Column(name = "total_score", nullable = false, precision = 6, scale = 2)
    private BigDecimal totalScore = BigDecimal.ZERO;

    /** Giai thich vi sao duoc de xuat, hien thi o man hinh chi tiet (2.2.30). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reason_json")
    private Map<String, Object> reasonJson;

    @Column(name = "status", nullable = false, length = 30)
    private SuggestionStatus status = SuggestionStatus.ACTIVE;
}
