package com.courtly.api.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.api.admin.AdminApiTestBase;
import com.courtly.domain.account.User;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 2.2.16, 2.2.19 - nhap, kich hoat va quay lai phien ban model AI. */
class AdminAiModelTest extends AdminApiTestBase {

    private static final String PATH = "/api/v1/admin/ai-models";
    private static final Path ARTIFACTS;

    static {
        try {
            ARTIFACTS = Files.createTempDirectory("courtly-artifacts");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @DynamicPropertySource
    static void ai(DynamicPropertyRegistry registry) {
        AiTestSupport.register(registry);
        registry.add("courtly.ai.artifact-dir", ARTIFACTS::toString);
    }

    @Autowired private JdbcTemplate jdbc;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    private final StubAiServer stub = AiTestSupport.STUB;
    private User admin;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        admin = persistAdmin("ai-admin");
        adminToken = loginAs(admin);
        stub.reset();
    }

    /** Tao thu muc artifact gia: file model.cbm + metadata.json dung dinh dang cua ai-service. */
    private String artifact(String kind, boolean gate, String checksumOverride) throws Exception {
        String version = (kind + "-" + UUID.randomUUID()).substring(0, 30);
        Path dir = Files.createDirectories(ARTIFACTS.resolve(version));
        byte[] bytes = ("model-" + version).getBytes(StandardCharsets.UTF_8);
        Files.write(dir.resolve("model.cbm"), bytes);
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        String metadata = objectMapper.writeValueAsString(Map.of(
                "kind", kind, "modelVersion", version, "checksum", checksumOverride == null ? checksum : checksumOverride,
                "trainedAt", "2026-11-19T01:00:00Z", "seed", 42, "schema", Map.of("version", 1, "features", List.of("a")),
                "parameters", Map.of("depth", 6),
                "metrics", Map.of("model", Map.of("overall", Map.of("ndcg@10", 0.6)), "baseline", Map.of("overall", Map.of("ndcg@10", 0.3))),
                "gate", gate ? Map.of("passes", true, "reasons", List.of()) : Map.of("passes", false, "reasons", List.of("NDCG@10 chua hon baseline"))));
        Files.writeString(dir.resolve("metadata.json"), metadata);
        return version;
    }

    private JsonNode call(String method, String path, String json, int expected, String token) throws Exception {
        var request = "GET".equals(method) ? get(PATH + path) : post(PATH + path);
        request = request.header("Authorization", bearer(token));
        if (json != null) request = request.contentType(MediaType.APPLICATION_JSON).content(json);
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private String importModel(String type, String version, int expected) throws Exception {
        JsonNode result = call("POST", "/import", "{\"modelType\":\"" + type + "\",\"modelVersion\":\"" + version + "\"}", expected, adminToken);
        return expected == 201 ? result.get("id").asText() : null;
    }

    private String stateOf(String id) {
        return jdbc.queryForObject("SELECT status FROM ai_models WHERE id = ?", String.class, UUID.fromString(id));
    }

    @Test
    @DisplayName("Nhap model: doc metadata, kiem tra checksum, tao ban ghi ready kem metric/gate/schema; trung phien ban, sai loai, checksum sai, duong dan la, khong co artifact bi tu choi")
    void importsAndValidates() throws Exception {
        String version = artifact("partner", true, null);
        String id = importModel("catboost_ranker", version, 201);
        assertThat(stateOf(id)).isEqualTo("ready");
        assertThat(jdbc.queryForObject("SELECT artifact_path || '|' || (metrics_json -> 'gate' ->> 'passes') || '|' || (feature_schema_json ->> 'version') FROM ai_models WHERE id = ?", String.class, UUID.fromString(id)))
                .isEqualTo(version + "/model.cbm|true|1");
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'ai_model.import' AND entity_id = ?", Integer.class, UUID.fromString(id))).isEqualTo(1);
        importModel("catboost_ranker", version, 409);
        importModel("catboost_team_ranker", version, 400);
        importModel("catboost_ranker", artifact("partner", true, "0".repeat(64)), 400);
        importModel("catboost_ranker", "../etc", 400);
        importModel("catboost_ranker", "khong-co-phien-ban-nay", 404);
        importModel("baseline_ranker", version, 400);
        call("POST", "/import", "{}", 400, adminToken);
    }

    @Test
    @DisplayName("Danh sach: model da nhap kem metric, model co the nhap, nguon diem hien hanh (baseline khi chua co AI), trang thai AI service")
    void lists() throws Exception {
        String importable = artifact("team", true, null);
        String version = artifact("partner", false, null);
        String id = importModel("catboost_ranker", version, 201);
        JsonNode result = call("GET", "", null, 200, adminToken);
        JsonNode model = null;
        for (JsonNode item : result.get("models")) if (item.get("id").asText().equals(id)) model = item;
        assertThat(model).isNotNull();
        assertThat(model.get("kind").asText()).isEqualTo("partner");
        assertThat(model.get("gatePasses").asBoolean()).isFalse();
        assertThat(model.get("canActivate").asBoolean()).isFalse();
        assertThat(model.get("gateReasons").get(0).asText()).contains("NDCG");
        assertThat(model.get("metrics").get("model").asDouble()).isEqualTo(0.6);
        assertThat(model.get("metrics").get("baseline").asDouble()).isEqualTo(0.3);
        boolean listed = false;
        for (JsonNode item : result.get("importable")) if (item.get("modelVersion").asText().equals(importable)) listed = true;
        assertThat(listed).isTrue();
        assertThat(result.get("service").get("configured").asBoolean()).isTrue();
        assertThat(result.get("service").get("reachable").asBoolean()).isTrue();
        call("GET", "", null, 403, loginAs(persistUser("not-admin", com.courtly.domain.account.Role.PLAYER)));
    }

    @Test
    @DisplayName("Kich hoat: chi khi dat kiem thu va AI service nap thanh cong; model cu cung loai thanh archived; AI service loi thi khong doi trang thai")
    void activates() throws Exception {
        String bad = importModel("catboost_ranker", artifact("partner", false, null), 201);
        JsonNode rejected = call("POST", "/" + bad + "/activate", null, 409, adminToken);
        assertThat(rejected.get("fieldErrors").get("gate").asText()).contains("NDCG");
        assertThat(stateOf(bad)).isEqualTo("ready");

        String first = importModel("catboost_ranker", artifact("partner", true, null), 201);
        stub.loadFails = true;
        assertThat(call("POST", "/" + first + "/activate", null, 409, adminToken).get("code").asText()).isEqualTo("AI_SERVICE_UNAVAILABLE");
        assertThat(stateOf(first)).isEqualTo("ready");
        stub.loadFails = false;

        call("POST", "/" + first + "/activate", null, 200, adminToken);
        assertThat(stateOf(first)).isEqualTo("active");
        assertThat(stub.loadRequests.getLast().get("modelType").asText()).isEqualTo("catboost_ranker");
        assertThat(stub.loadRequests.getLast().get("checksum").asText()).hasSize(64);
        call("POST", "/" + first + "/activate", null, 409, adminToken);

        String second = importModel("catboost_ranker", artifact("partner", true, null), 201);
        call("POST", "/" + second + "/activate", null, 200, adminToken);
        assertThat(stateOf(second)).isEqualTo("active");
        assertThat(stateOf(first)).isEqualTo("archived");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_models WHERE model_type = 'catboost_ranker' AND status = 'active'", Integer.class)).isEqualTo(1);
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'ai_model.activate'", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("Quay lai: chi model archived, nap lai AI service, model dang chay thanh archived; khong quay lai ready/failed; khong kich hoat baseline")
    void rollsBack() throws Exception {
        String first = importModel("catboost_ranker", artifact("partner", true, null), 201);
        String second = importModel("catboost_ranker", artifact("partner", true, null), 201);
        call("POST", "/" + first + "/activate", null, 200, adminToken);
        call("POST", "/" + second + "/activate", null, 200, adminToken);
        call("POST", "/" + second + "/rollback", null, 409, adminToken);
        int loads = stub.loadRequests.size();
        call("POST", "/" + first + "/rollback", null, 200, adminToken);
        assertThat(stateOf(first)).isEqualTo("active");
        assertThat(stateOf(second)).isEqualTo("archived");
        assertThat(stub.loadRequests).hasSize(loads + 1);
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'ai_model.rollback'", Integer.class)).isEqualTo(1);

        String ready = importModel("catboost_ranker", artifact("partner", true, null), 201);
        call("POST", "/" + ready + "/rollback", null, 409, adminToken);
        jdbc.update("UPDATE ai_models SET status = 'failed' WHERE id = ?", UUID.fromString(ready));
        entityManager.clear();
        call("POST", "/" + ready + "/activate", null, 409, adminToken);
        UUID baseline = jdbc.queryForObject("SELECT id FROM ai_models WHERE model_type = 'baseline_ranker' LIMIT 1", UUID.class);
        call("POST", "/" + baseline + "/activate", null, 400, adminToken);
        call("POST", "/" + UUID.randomUUID() + "/activate", null, 404, adminToken);
    }

    @Test
    @DisplayName("Chi quan tri vien co quyen matching.manage; nguoi choi/chu san 403, chua dang nhap 401")
    void permissions() throws Exception {
        String version = artifact("partner", true, null);
        String player = loginAs(persistUser("not-admin-2", com.courtly.domain.account.Role.PLAYER));
        call("POST", "/import", "{\"modelType\":\"catboost_ranker\",\"modelVersion\":\"" + version + "\"}", 403, player);
        call("POST", "/import", "{\"modelType\":\"catboost_ranker\",\"modelVersion\":\"" + version + "\"}", 403, ownerToken());
        mockMvc.perform(post(PATH + "/import").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    private String ownerToken() throws Exception {
        return loginAs(persistOwner("ai-owner"));
    }
}
