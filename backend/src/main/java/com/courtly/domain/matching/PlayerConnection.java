package com.courtly.domain.matching;

import com.courtly.common.BaseEntity;
import com.courtly.common.enums.ConnectionStatus;
import com.courtly.domain.account.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Doi tac da ghep cap (2.2.36).
 *
 * <p>Luu hai chieu: khi chap nhan yeu cau thi tao hai ban ghi a-&gt;b va b-&gt;a
 * de truy van danh sach doi tac chi can loc theo user_id.
 */
@Entity
@Table(name = "player_connections", uniqueConstraints =
        @UniqueConstraint(name = "plc_unique", columnNames = {"user_id", "partner_id"}))
@Getter
@Setter
@NoArgsConstructor
public class PlayerConnection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false)
    private User partner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_request_id")
    private PartnerRequest sourceRequest;

    @Column(name = "status", nullable = false, length = 30)
    private ConnectionStatus status = ConnectionStatus.ACTIVE;
}
