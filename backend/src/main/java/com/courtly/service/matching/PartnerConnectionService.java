package com.courtly.service.matching;

import com.courtly.api.partner.dto.PartnerConnectionResponse;
import com.courtly.common.PageResponse;
import com.courtly.common.enums.ConnectionStatus;
import com.courtly.common.enums.UserStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.PlayerProfile;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.matching.PlayerConnection;
import com.courtly.domain.matching.PlayerConnectionRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Truy van danh sach doi tac da ket noi (2.2.36). */
@Service
@RequiredArgsConstructor
public class PartnerConnectionService {

    private final UserRepository userRepository;
    private final PlayerConnectionRepository connectionRepository;

    @Transactional(readOnly = true)
    public PageResponse<PartnerConnectionResponse> listActive(UUID userId, int page, int size) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay tai khoan."));
        if (user.getStatus() != UserStatus.ACTIVE || !userRepository.hasPlayerRole(userId)) {
            throw ApiException.forbidden(ErrorCode.FORBIDDEN,
                    "Tai khoan cua ban khong the xem danh sach doi tac.");
        }
        Page<PlayerConnection> connections = connectionRepository.findByUserAndStatus(
                userId, ConnectionStatus.ACTIVE, PageRequest.of(page, size));
        return PageResponse.from(connections, connections.getContent().stream()
                .map(PartnerConnectionService::toResponse)
                .toList());
    }

    private static PartnerConnectionResponse toResponse(PlayerConnection connection) {
        User partner = connection.getPartner();
        PlayerProfile profile = partner.getPlayerProfile();
        return new PartnerConnectionResponse(
                connection.getId(),
                partner.getId(),
                partner.getFullName(),
                partner.getAvatarUrl(),
                profile == null || profile.getPlayingStyle() == null
                        ? null : profile.getPlayingStyle().getValue(),
                profile == null || profile.getPreferredPlayType() == null
                        ? null : profile.getPreferredPlayType().getValue(),
                connection.getCreatedAt());
    }
}
