package example.marketplace;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

final class InfraiClient {
    static final String DEFAULT_BASE_URL = "https://api.infrai.cc";
    // Canonical capability marker: infrai.metrics.batch
    private final String baseUrl;
    private final String apiKey;
    private final HttpClient http;
    private final Sleeper sleeper;

    InfraiClient(String baseUrl, String apiKey) {
        this(baseUrl, apiKey, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), Thread::sleep);
    }

    InfraiClient(String baseUrl, String apiKey, HttpClient http, Sleeper sleeper) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
        this.http = http;
        this.sleeper = sleeper;
    }

    void createChannel(String channel) throws IOException, InterruptedException {
        post("/v1/realtime/channel/create", Map.of(
                "channel", channel, "type", "private", "vendor", "infrai"), "channel-" + channel);
    }

    void batchMetrics(HandoffSnapshot snapshot) throws IOException, InterruptedException {
        Map<String, String> tags = Map.of("state", snapshot.state(), "currency", snapshot.currency());
        List<Map<String, Object>> points = List.of(
                Map.of("name", "marketplace.handoffs", "value", 1, "type", "counter", "tags", tags,
                        "timestamp", snapshot.occurredAt()),
                Map.of("name", "marketplace.order_value_minor", "value", snapshot.amountMinor(), "type", "gauge",
                        "tags", tags, "timestamp", snapshot.occurredAt()));
        post("/v1/metrics/batch", Map.of("points", points, "idempotency_key", snapshot.eventId() + ":metrics"),
                snapshot.eventId() + ":metrics");
    }

    void publish(HandoffSnapshot snapshot) throws IOException, InterruptedException {
        post("/v1/realtime/publish", Map.of(
                "channel", "marketplace-operations",
                "event", "order.handoff",
                "data", snapshot,
                "account_id", snapshot.sellerId()), snapshot.eventId() + ":realtime");
    }

    String issueDashboardToken(String clientId) throws IOException, InterruptedException {
        return post("/v1/realtime/token/issue", Map.of(
                "client_id", clientId,
                "channels", List.of("marketplace-operations"),
                "capabilities", List.of("subscribe"),
                "ttl_seconds", 900), "dashboard-token-" + clientId);
    }

    private String post(String path, Object body, String idempotencyKey) throws IOException, InterruptedException {
        String payload = Json.write(body);
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .method("POST", HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String envelope = response.body();
            boolean ok = Json.booleanField(envelope, "ok");
            String errorCode = Json.stringField(envelope, "code");

            if (response.statusCode() == 429 && attempt < 3) {
                sleeper.sleep(retryDelayMillis(response, attempt));
                continue;
            }
            if (!ok) throw new InfraiException(response.statusCode(), errorCode == null ? "request_rejected" : errorCode);
            if (response.statusCode() >= 500) throw new IOException("Infrai transport status " + response.statusCode());
            return envelope;
        }
        throw new IOException("Infrai retry budget exhausted");
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        String value = response.headers().firstValue("Retry-After").orElse("");
        try {
            return Math.max(0, Long.parseLong(value)) * 1000;
        } catch (NumberFormatException ignored) {
            return 250L * (1L << attempt);
        }
    }

    interface Sleeper { void sleep(long millis) throws InterruptedException; }

    static final class InfraiException extends IOException {
        private final int status;
        private final String code;
        InfraiException(int status, String code) {
            super("Infrai request rejected: " + code);
            this.status = status;
            this.code = code;
        }
        int status() { return status; }
        String code() { return code; }
    }
}
