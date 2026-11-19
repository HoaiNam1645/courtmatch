package com.courtly.api.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** AI service gia cho test Spring Boot: ghi lai yeu cau va tra ket qua theo che do cau hinh duoc. */
public final class StubAiServer {

    public enum Mode { NORMAL, SLOW, UNAVAILABLE, WRONG_VERSION, DROP_ONE, EXTRA_ONE, BAD_SCORE, DUPLICATE }

    public static final String TOKEN = "test-ai-token";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpServer server;
    public volatile Mode mode = Mode.NORMAL;
    public volatile String partnerVersion = "test-partner-1";
    public volatile String teamVersion = "test-team-1";
    /** Diem 0-100 cua mot ung vien theo dac trung; mac dinh: ung vien Elo cao hon diem cao hon (khac thu tu baseline). */
    public volatile Function<JsonNode, Double> scorer = f -> Math.min(99.0, 10.0 + f.get("c_rating").asDouble() / 20.0);
    /** Xac suat doi 1 thang cua phuong an theo rank (1..3). */
    public volatile Function<Integer, Double> probability = rank -> rank == 2 ? 0.5 : 0.8;
    public final List<JsonNode> rankRequests = new CopyOnWriteArrayList<>();
    public final List<JsonNode> balanceRequests = new CopyOnWriteArrayList<>();
    public final List<String> tokens = new CopyOnWriteArrayList<>();
    public final List<JsonNode> loadRequests = new CopyOnWriteArrayList<>();
    public volatile boolean loadFails;

    public StubAiServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        server.createContext("/health", exchange -> respond(exchange, 200, "{\"status\":\"ok\",\"models\":{}}"));
        server.createContext("/v1/models/load", exchange -> {
            JsonNode body = MAPPER.readTree(exchange.getRequestBody());
            loadRequests.add(body);
            if (loadFails) {
                respond(exchange, 409, "{\"detail\":\"checksum\"}");
                return;
            }
            respond(exchange, 200, "{\"modelVersion\":\"" + body.get("modelVersion").asText() + "\",\"checksum\":\""
                    + (body.hasNonNull("checksum") ? body.get("checksum").asText() : "") + "\",\"kind\":\"x\"}");
        });
        server.createContext("/v1/rank-partners", exchange -> {
            tokens.add(String.valueOf(exchange.getRequestHeaders().getFirst("X-Internal-Token")));
            JsonNode body = MAPPER.readTree(exchange.getRequestBody());
            rankRequests.add(body);
            if (!handleMode(exchange)) return;
            ObjectNode out = MAPPER.createObjectNode();
            out.put("modelVersion", mode == Mode.WRONG_VERSION ? "other-version" : partnerVersion);
            out.put("scoringSource", "ai");
            ArrayNode results = out.putArray("results");
            List<JsonNode> candidates = new ArrayList<>();
            body.get("candidates").forEach(candidates::add);
            for (int i = 0; i < candidates.size(); i++) {
                if (mode == Mode.DROP_ONE && i == 0) continue;
                JsonNode candidate = candidates.get(i);
                ObjectNode item = results.addObject();
                item.put("userId", candidate.get("userId").asText());
                item.put("totalScore", mode == Mode.BAD_SCORE && i == 0 ? 150.0 : scorer.apply(candidate.get("features")));
                item.put("confidence", 0.82);
                ArrayNode reasons = item.putArray("reasons");
                reasons.addObject().put("code", "SKILL_BALANCED").put("weight", 1.2);
                reasons.addObject().put("code", "SAME_TIME").put("weight", 0.7);
            }
            if (mode == Mode.EXTRA_ONE) {
                ObjectNode extra = results.addObject();
                extra.put("userId", java.util.UUID.randomUUID().toString());
                extra.put("totalScore", 99.0).put("confidence", 0.9);
                extra.putArray("reasons");
            }
            if (mode == Mode.DUPLICATE && results.size() > 1) {
                results.get(1).deepCopy();
                ((ObjectNode) results.get(1)).put("userId", results.get(0).get("userId").asText());
            }
            respond(exchange, 200, out.toString());
        });
        server.createContext("/v1/predict-balance", exchange -> {
            JsonNode body = MAPPER.readTree(exchange.getRequestBody());
            balanceRequests.add(body);
            if (!handleMode(exchange)) return;
            ObjectNode out = MAPPER.createObjectNode();
            out.put("modelVersion", mode == Mode.WRONG_VERSION ? "other-version" : teamVersion);
            ArrayNode results = out.putArray("results");
            int index = 0;
            for (JsonNode option : body.get("options")) {
                if (mode == Mode.DROP_ONE && index++ == 0) continue;
                double p = probability.apply(Integer.parseInt(option.get("optionId").asText()));
                ObjectNode item = results.addObject();
                item.put("optionId", option.get("optionId").asText());
                item.put("team1WinProbability", mode == Mode.BAD_SCORE ? 1.7 : p);
                item.put("balance", 100.0 * (1.0 - Math.abs(2 * p - 1)));
            }
            respond(exchange, 200, out.toString());
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool()); // che do SLOW khong duoc chan cac yeu cau khac
        server.start();
    }

    private boolean handleMode(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        if (mode == Mode.UNAVAILABLE) {
            respond(exchange, 503, "{\"detail\":\"Chua nap model\"}");
            return false;
        }
        if (mode == Mode.SLOW) {
            try {
                Thread.sleep(2500);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        return true;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        try {
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException ignored) {
            // client da het han va dong ket noi (che do SLOW)
        } finally {
            exchange.close();
        }
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void reset() {
        mode = Mode.NORMAL;
        rankRequests.clear();
        balanceRequests.clear();
        tokens.clear();
        loadRequests.clear();
        loadFails = false;
    }

    public void stop() {
        server.stop(0);
    }
}
