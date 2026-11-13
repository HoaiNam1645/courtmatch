package com.courtly.api.owner;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.UserStatus;
import com.courtly.common.enums.VerificationStatus;
import com.courtly.domain.account.CourtOwnerProfile;
import com.courtly.domain.account.CourtOwnerProfileRepository;
import com.courtly.domain.account.PlayerProfile;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.RoleRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.account.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Du lieu va thao tac dung chung cho test cac API cua chu san (2.3.x). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class OwnerApiTestBase {

    protected static final String PASSWORD = "Courtly@123";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserRepository userRepository;
    @Autowired protected RoleRepository roleRepository;
    @Autowired protected CourtOwnerProfileRepository ownerProfileRepository;
    @Autowired protected PasswordEncoder passwordEncoder;

    protected void ensureRoles() {
        ensureRole(Role.PLAYER, "Nguoi choi");
        ensureRole(Role.OWNER, "Chu san");
    }

    protected User persistOwner(String prefix) {
        User owner = persistUser(prefix, Role.OWNER);
        CourtOwnerProfile profile = new CourtOwnerProfile(owner);
        profile.setBusinessName("Ho kinh doanh " + prefix);
        profile.setBankName("MB Bank");
        profile.setBankAccountNo("0359111001");
        profile.setBankAccountName("NGUYEN HUU THANG");
        profile.setVerificationStatus(VerificationStatus.VERIFIED);
        ownerProfileRepository.saveAndFlush(profile);
        return owner;
    }

    protected User persistUser(String prefix, String roleCode) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Nguoi Dung " + prefix);
        user.setEmail(prefix + "-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setStatus(UserStatus.ACTIVE);
        user.getUserRoles().add(new UserRole(user, roleRepository.findByCode(roleCode).orElseThrow()));
        if (Role.PLAYER.equals(roleCode)) {
            user.setPlayerProfile(new PlayerProfile(user));
        }
        return userRepository.saveAndFlush(user);
    }

    protected String loginAs(User user) throws Exception {
        return loginAs(user.getEmail(), PASSWORD);
    }

    protected String loginAs(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailOrPhone":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    private void ensureRole(String code, String name) {
        if (roleRepository.findByCode(code).isEmpty()) {
            Role role = new Role(code, name, null);
            role.setId(UUID.randomUUID());
            roleRepository.save(role);
        }
    }
}
