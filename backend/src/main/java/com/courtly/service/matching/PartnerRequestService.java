package com.courtly.service.matching;

import com.courtly.api.partner.dto.CreatePartnerRequestRequest;
import com.courtly.api.partner.dto.PartnerRequestResponse;
import com.courtly.common.PageResponse;
import com.courtly.common.enums.ConnectionStatus;
import com.courtly.common.enums.PartnerRequestStatus;
import com.courtly.common.enums.UserStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.matching.PartnerRequest;
import com.courtly.domain.matching.PartnerRequestRepository;
import com.courtly.domain.matching.PlayerConnectionRepository;
import com.courtly.domain.matching.PlayerConnection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Xu ly vong doi yeu cau ghep cap doi tac (2.2.31 - 2.2.35). */
@Service
@RequiredArgsConstructor
public class PartnerRequestService {

    private final UserRepository userRepository;
    private final PartnerRequestRepository requestRepository;
    private final PlayerConnectionRepository connectionRepository;

    @Transactional
    public PartnerRequestResponse create(UUID requesterId, CreatePartnerRequestRequest input) {
        User requester = requireActivePlayer(requesterId, "Tai khoan cua ban khong the gui yeu cau ghep cap.");
        if (requesterId.equals(input.receiverId())) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Ban khong the gui yeu cau ghep cap cho chinh minh.",
                    Map.of("receiverId", "Hay chon mot nguoi choi khac"));
        }
        User receiver = requireActivePlayer(input.receiverId(), "Nguoi choi nay hien khong the nhan loi moi.");
        if (receiver.getEmail() != null && receiver.getEmail().toLowerCase().endsWith("@bwf.example")) {
            throw ApiException.notFound("Khong tim thay nguoi choi.");
        }
        if (connectionRepository.existsByUserIdAndPartnerIdAndStatus(
                requesterId, receiver.getId(), ConnectionStatus.ACTIVE)) {
            throw ApiException.conflict(ErrorCode.PARTNER_ALREADY_CONNECTED,
                    "Hai ban da la doi tac cua nhau.", Map.of("receiverId", "Doi tac da duoc ket noi"));
        }
        if (requestRepository.existsBetweenUsersWithStatus(
                requesterId, receiver.getId(), PartnerRequestStatus.PENDING)) {
            throw duplicateRequest();
        }

        PartnerRequest request = new PartnerRequest();
        request.setRequester(requester);
        request.setReceiver(receiver);
        request.setMessage(input.message().trim());
        try {
            return toResponse(requestRepository.saveAndFlush(request));
        } catch (DataIntegrityViolationException exception) {
            throw duplicateRequest();
        }
    }

    /** 2.2.32 - danh sach yeu cau ma nguoi choi dang nhap da nhan. */
    @Transactional(readOnly = true)
    public PageResponse<PartnerRequestResponse> received(
            UUID receiverId, String statusValue, int page, int size) {
        requireActivePlayer(receiverId, "Tai khoan cua ban khong the xem yeu cau ghep cap.");
        PartnerRequestStatus status = parseOptionalStatus(statusValue);
        Page<PartnerRequest> requests = requestRepository.findReceived(
                receiverId, status, PageRequest.of(page, size));
        return PageResponse.from(requests, requests.getContent().stream()
                .map(PartnerRequestService::toResponse)
                .toList());
    }

    /** Danh sach yeu cau da gui, can de nguoi gui thao tac huy o 2.2.35. */
    @Transactional(readOnly = true)
    public PageResponse<PartnerRequestResponse> sent(
            UUID requesterId, String statusValue, int page, int size) {
        requireActivePlayer(requesterId, "Tai khoan cua ban khong the xem yeu cau ghep cap.");
        PartnerRequestStatus status = parseOptionalStatus(statusValue);
        Page<PartnerRequest> requests = requestRepository.findSent(
                requesterId, status, PageRequest.of(page, size));
        return PageResponse.from(requests, requests.getContent().stream()
                .map(PartnerRequestService::toResponse)
                .toList());
    }

    /** 2.2.33 - nguoi nhan chap nhan va tao ket noi hai chieu trong cung transaction. */
    @Transactional
    public PartnerRequestResponse accept(UUID receiverId, UUID requestId) {
        PartnerRequest request = requirePendingRequestForReceiver(receiverId, requestId);
        request.setStatus(PartnerRequestStatus.ACCEPTED);
        request.setRespondedAt(Instant.now());

        PlayerConnection forward = new PlayerConnection();
        forward.setUser(request.getRequester());
        forward.setPartner(request.getReceiver());
        forward.setSourceRequest(request);
        forward.setStatus(ConnectionStatus.ACTIVE);

        PlayerConnection reverse = new PlayerConnection();
        reverse.setUser(request.getReceiver());
        reverse.setPartner(request.getRequester());
        reverse.setSourceRequest(request);
        reverse.setStatus(ConnectionStatus.ACTIVE);

        try {
            connectionRepository.saveAllAndFlush(List.of(forward, reverse));
            return toResponse(requestRepository.saveAndFlush(request));
        } catch (DataIntegrityViolationException exception) {
            throw ApiException.conflict(ErrorCode.PARTNER_ALREADY_CONNECTED,
                    "Hai ban da la doi tac cua nhau.", Map.of());
        }
    }

    /** 2.2.34 - nguoi nhan tu choi mot yeu cau dang cho. */
    @Transactional
    public PartnerRequestResponse reject(UUID receiverId, UUID requestId) {
        PartnerRequest request = requirePendingRequestForReceiver(receiverId, requestId);
        request.setStatus(PartnerRequestStatus.REJECTED);
        request.setRespondedAt(Instant.now());
        return toResponse(requestRepository.saveAndFlush(request));
    }

    /** 2.2.35 - chi nguoi gui duoc huy yeu cau dang cho cua minh. */
    @Transactional
    public PartnerRequestResponse cancel(UUID requesterId, UUID requestId) {
        PartnerRequest request = requestRepository.findDetailByIdForUpdate(requestId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay yeu cau ghep cap."));
        if (!request.getRequester().getId().equals(requesterId)) {
            throw ApiException.forbidden(ErrorCode.FORBIDDEN,
                    "Chi nguoi gui moi co the huy yeu cau nay.");
        }
        if (request.getStatus() != PartnerRequestStatus.PENDING) {
            throw ApiException.conflict(ErrorCode.PARTNER_REQUEST_STATE_INVALID,
                    "Yeu cau nay khong con o trang thai cho huy.", Map.of());
        }
        request.setStatus(PartnerRequestStatus.CANCELLED);
        request.setRespondedAt(Instant.now());
        return toResponse(requestRepository.saveAndFlush(request));
    }

    private User requireActivePlayer(UUID userId, String unavailableMessage) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay nguoi choi."));
        if (user.getStatus() != UserStatus.ACTIVE || !userRepository.hasPlayerRole(userId)) {
            throw ApiException.forbidden(ErrorCode.FORBIDDEN, unavailableMessage);
        }
        return user;
    }

    private PartnerRequest requirePendingRequestForReceiver(UUID receiverId, UUID requestId) {
        PartnerRequest request = requestRepository.findDetailByIdForUpdate(requestId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay yeu cau ghep cap."));
        if (!request.getReceiver().getId().equals(receiverId)) {
            throw ApiException.forbidden(ErrorCode.FORBIDDEN,
                    "Chi nguoi nhan moi co the xu ly yeu cau nay.");
        }
        if (request.getStatus() != PartnerRequestStatus.PENDING) {
            throw ApiException.conflict(ErrorCode.PARTNER_REQUEST_STATE_INVALID,
                    "Yeu cau nay khong con o trang thai cho xu ly.", Map.of());
        }
        return request;
    }

    private static ApiException duplicateRequest() {
        return ApiException.conflict(ErrorCode.PARTNER_REQUEST_DUPLICATE,
                "Giua hai ban da co mot yeu cau dang cho xu ly.",
                Map.of("receiverId", "Hay cho yeu cau hien tai duoc xu ly"));
    }

    private static PartnerRequestStatus parseOptionalStatus(String value) {
        if (value == null || value.isBlank() || "all".equals(value)) return null;
        try {
            return PartnerRequestStatus.from(value);
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Trang thai yeu cau khong hop le.",
                    Map.of("status", "Hay chon mot trang thai hop le"));
        }
    }

    static PartnerRequestResponse toResponse(PartnerRequest request) {
        return new PartnerRequestResponse(
                request.getId(),
                toPlayer(request.getRequester()),
                toPlayer(request.getReceiver()),
                request.getMessage(),
                request.getStatus(),
                request.getRespondedAt(),
                request.getCreatedAt());
    }

    private static PartnerRequestResponse.Player toPlayer(User user) {
        return new PartnerRequestResponse.Player(user.getId(), user.getFullName(), user.getAvatarUrl());
    }
}
