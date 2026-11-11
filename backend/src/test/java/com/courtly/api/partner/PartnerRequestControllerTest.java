package com.courtly.api.partner;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.ConnectionStatus;
import com.courtly.common.enums.PartnerRequestStatus;
import com.courtly.common.enums.UserStatus;
import com.courtly.domain.account.PlayerProfile;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.RoleRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.account.UserRole;
import com.courtly.domain.matching.PartnerRequest;
import com.courtly.domain.matching.PartnerRequestRepository;
import com.courtly.domain.matching.PlayerConnection;
import com.courtly.domain.matching.PlayerConnectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PartnerRequestControllerTest {

    private static final String PASSWORD = "Courtly@123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PartnerRequestRepository requestRepository;
    @Autowired private PlayerConnectionRepository connectionRepository;

    private User requester;
    private User receiver;
    private String requesterToken;
    private String receiverToken;

    @BeforeEach
    void setUp() throws Exception {
        ensurePlayerRole();
        requester = persistPlayer("requester");
        receiver = persistPlayer("receiver");
        requesterToken = loginAs(requester);
        receiverToken = loginAs(receiver);
    }

    @Test
    @DisplayName("2.2.31 gui yeu cau ghep cap va chuan hoa loi nhan")
    void createsPartnerRequest() throws Exception {
        mockMvc.perform(post("/api/v1/partner-requests")
                        .header("Authorization", bearer(requesterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"receiverId":"%s","message":"  Toi thu Nam minh danh nhe?  "}
                                """.formatted(receiver.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requester.id").value(requester.getId().toString()))
                .andExpect(jsonPath("$.receiver.id").value(receiver.getId().toString()))
                .andExpect(jsonPath("$.message").value("Toi thu Nam minh danh nhe?"))
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("2.2.31 chan tu gui, loi nhan rong va request khong dang nhap")
    void validatesCreateInputAndAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/partner-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"receiverId":"%s","message":"Hen danh nhe"}
                                """.formatted(receiver.getId())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/partner-requests")
                        .header("Authorization", bearer(requesterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"receiverId":"%s","message":"   "}
                                """.formatted(receiver.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.message").isNotEmpty());

        mockMvc.perform(post("/api/v1/partner-requests")
                        .header("Authorization", bearer(requesterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"receiverId":"%s","message":"Hen danh nhe"}
                                """.formatted(requester.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.receiverId").isNotEmpty());
    }

    @Test
    @DisplayName("2.2.31 chan yeu cau dang cho theo ca hai chieu")
    void rejectsPendingRequestInEitherDirection() throws Exception {
        persistRequest(receiver, requester, PartnerRequestStatus.PENDING);

        mockMvc.perform(post("/api/v1/partner-requests")
                        .header("Authorization", bearer(requesterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"receiverId":"%s","message":"Gui trung"}
                                """.formatted(receiver.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PARTNER_REQUEST_DUPLICATE"));
    }

    @Test
    @DisplayName("2.2.31 khong gui loi moi cho doi tac da ket noi")
    void rejectsAlreadyConnectedPlayer() throws Exception {
        PlayerConnection connection = new PlayerConnection();
        connection.setUser(requester);
        connection.setPartner(receiver);
        connection.setStatus(ConnectionStatus.ACTIVE);
        connectionRepository.saveAndFlush(connection);

        mockMvc.perform(post("/api/v1/partner-requests")
                        .header("Authorization", bearer(requesterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"receiverId":"%s","message":"Minh choi nhe"}
                                """.formatted(receiver.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PARTNER_ALREADY_CONNECTED"));
    }

    @Test
    @DisplayName("2.2.32 xem va loc phan trang cac yeu cau da nhan")
    void listsReceivedRequests() throws Exception {
        persistRequest(receiver, requester, PartnerRequestStatus.PENDING);
        persistRequest(persistPlayer("other-sender"), requester, PartnerRequestStatus.REJECTED);
        persistRequest(requester, receiver, PartnerRequestStatus.PENDING);

        mockMvc.perform(get("/api/v1/partner-requests/received")
                        .header("Authorization", bearer(requesterToken))
                        .param("status", "pending")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].requester.id").value(receiver.getId().toString()))
                .andExpect(jsonPath("$.content[0].receiver.id").value(requester.getId().toString()))
                .andExpect(jsonPath("$.content[0].status").value("pending"));

        mockMvc.perform(get("/api/v1/partner-requests/received")
                        .header("Authorization", bearer(requesterToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("2.2.32 tu choi bo loc va phan trang khong hop le")
    void validatesReceivedFilters() throws Exception {
        mockMvc.perform(get("/api/v1/partner-requests/received")
                        .header("Authorization", bearer(requesterToken))
                        .param("status", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").isNotEmpty());

        mockMvc.perform(get("/api/v1/partner-requests/received")
                        .header("Authorization", bearer(requesterToken))
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("2.2.33 nguoi nhan chap nhan va tao ket noi hai chieu")
    void acceptsPendingRequest() throws Exception {
        PartnerRequest request = persistRequest(requester, receiver, PartnerRequestStatus.PENDING);

        mockMvc.perform(post("/api/v1/partner-requests/{requestId}/accept", request.getId())
                        .header("Authorization", bearer(receiverToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.respondedAt").isNotEmpty());

        org.assertj.core.api.Assertions.assertThat(connectionRepository
                .existsByUserIdAndPartnerIdAndStatus(
                        requester.getId(), receiver.getId(), ConnectionStatus.ACTIVE)).isTrue();
        org.assertj.core.api.Assertions.assertThat(connectionRepository
                .existsByUserIdAndPartnerIdAndStatus(
                        receiver.getId(), requester.getId(), ConnectionStatus.ACTIVE)).isTrue();
    }

    @Test
    @DisplayName("2.2.33 chan sai nguoi xu ly va yeu cau khong con cho")
    void validatesAcceptAuthorizationAndState() throws Exception {
        PartnerRequest pending = persistRequest(requester, receiver, PartnerRequestStatus.PENDING);
        mockMvc.perform(post("/api/v1/partner-requests/{requestId}/accept", pending.getId())
                        .header("Authorization", bearer(requesterToken)))
                .andExpect(status().isForbidden());

        PartnerRequest rejected = persistRequest(requester, receiver, PartnerRequestStatus.REJECTED);
        mockMvc.perform(post("/api/v1/partner-requests/{requestId}/accept", rejected.getId())
                        .header("Authorization", bearer(receiverToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PARTNER_REQUEST_STATE_INVALID"));
    }

    @Test
    @DisplayName("2.2.34 nguoi nhan tu choi va luu thoi diem phan hoi")
    void rejectsPendingRequest() throws Exception {
        PartnerRequest request = persistRequest(requester, receiver, PartnerRequestStatus.PENDING);

        mockMvc.perform(post("/api/v1/partner-requests/{requestId}/reject", request.getId())
                        .header("Authorization", bearer(receiverToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.respondedAt").isNotEmpty());
    }

    @Test
    @DisplayName("2.2.34 chan nguoi gui va yeu cau da xu ly tu choi lai")
    void validatesRejectAuthorizationAndState() throws Exception {
        PartnerRequest pending = persistRequest(requester, receiver, PartnerRequestStatus.PENDING);
        mockMvc.perform(post("/api/v1/partner-requests/{requestId}/reject", pending.getId())
                        .header("Authorization", bearer(requesterToken)))
                .andExpect(status().isForbidden());

        PartnerRequest accepted = persistRequest(requester, receiver, PartnerRequestStatus.ACCEPTED);
        mockMvc.perform(post("/api/v1/partner-requests/{requestId}/reject", accepted.getId())
                        .header("Authorization", bearer(receiverToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PARTNER_REQUEST_STATE_INVALID"));
    }

    @Test
    @DisplayName("2.2.35 xem yeu cau da gui va huy yeu cau dang cho")
    void listsAndCancelsSentRequest() throws Exception {
        PartnerRequest pending = persistRequest(requester, receiver, PartnerRequestStatus.PENDING);
        persistRequest(requester, persistPlayer("other-receiver"), PartnerRequestStatus.REJECTED);
        persistRequest(receiver, requester, PartnerRequestStatus.PENDING);

        mockMvc.perform(get("/api/v1/partner-requests/sent")
                        .header("Authorization", bearer(requesterToken))
                        .param("status", "pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(pending.getId().toString()));

        mockMvc.perform(post("/api/v1/partner-requests/{requestId}/cancel", pending.getId())
                        .header("Authorization", bearer(requesterToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.respondedAt").isNotEmpty());
    }

    @Test
    @DisplayName("2.2.35 chan nguoi nhan va yeu cau da xu ly huy lai")
    void validatesCancelAuthorizationAndState() throws Exception {
        PartnerRequest pending = persistRequest(requester, receiver, PartnerRequestStatus.PENDING);
        mockMvc.perform(post("/api/v1/partner-requests/{requestId}/cancel", pending.getId())
                        .header("Authorization", bearer(receiverToken)))
                .andExpect(status().isForbidden());

        PartnerRequest rejected = persistRequest(requester, receiver, PartnerRequestStatus.REJECTED);
        mockMvc.perform(post("/api/v1/partner-requests/{requestId}/cancel", rejected.getId())
                        .header("Authorization", bearer(requesterToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PARTNER_REQUEST_STATE_INVALID"));
    }

    @Test
    @DisplayName("2.2.36 chi xem ket noi doi tac dang hoat dong")
    void listsOnlyActiveConnections() throws Exception {
        PlayerConnection active = persistConnection(requester, receiver, ConnectionStatus.ACTIVE);
        User removedPartner = persistPlayer("removed-partner");
        persistConnection(requester, removedPartner, ConnectionStatus.REMOVED);
        persistRequest(persistPlayer("pending-sender"), requester, PartnerRequestStatus.PENDING);

        mockMvc.perform(get("/api/v1/partners/connections")
                        .header("Authorization", bearer(requesterToken))
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].connectionId").value(active.getId().toString()))
                .andExpect(jsonPath("$.content[0].partnerId").value(receiver.getId().toString()))
                .andExpect(jsonPath("$.content[0].fullName").value(receiver.getFullName()))
                .andExpect(jsonPath("$.content[0].connectedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/partners/connections")
                        .header("Authorization", bearer(requesterToken))
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    private User persistPlayer(String prefix) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Nguoi Choi " + prefix);
        user.setEmail(prefix + "-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setStatus(UserStatus.ACTIVE);
        user.getUserRoles().add(new UserRole(
                user, roleRepository.findByCode(Role.PLAYER).orElseThrow()));
        user.setPlayerProfile(new PlayerProfile(user));
        return userRepository.saveAndFlush(user);
    }

    private PartnerRequest persistRequest(User sender, User target, PartnerRequestStatus status) {
        PartnerRequest request = new PartnerRequest();
        request.setRequester(sender);
        request.setReceiver(target);
        request.setMessage("Yeu cau co san");
        request.setStatus(status);
        return requestRepository.saveAndFlush(request);
    }

    private PlayerConnection persistConnection(
            User user, User partner, ConnectionStatus status) {
        PlayerConnection connection = new PlayerConnection();
        connection.setUser(user);
        connection.setPartner(partner);
        connection.setStatus(status);
        return connectionRepository.saveAndFlush(connection);
    }

    private void ensurePlayerRole() {
        if (roleRepository.findByCode(Role.PLAYER).isEmpty()) {
            Role role = new Role(Role.PLAYER, "Nguoi choi", null);
            role.setId(UUID.randomUUID());
            roleRepository.saveAndFlush(role);
        }
    }

    private String loginAs(User user) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailOrPhone":"%s","password":"%s"}
                                """.formatted(user.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
