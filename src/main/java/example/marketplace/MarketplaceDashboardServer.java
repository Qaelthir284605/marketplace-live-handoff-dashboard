package example.marketplace;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

public final class MarketplaceDashboardServer {
    private MarketplaceDashboardServer() {}

    public static void main(String[] args) throws Exception {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("INFRAI_API_KEY is required");
        String baseUrl = System.getenv().getOrDefault("INFRAI_BASE_URL", InfraiClient.DEFAULT_BASE_URL);
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        InfraiClient client = new InfraiClient(baseUrl, key);
        client.createChannel("marketplace-operations");
        MarketplaceOperationsService service = new MarketplaceOperationsService(client);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/order-handoffs", exchange -> handleHandoff(exchange, service));
        server.createContext("/dashboard-token", exchange -> handleToken(exchange, service));
        server.start();
        System.out.println("Marketplace operations service listening on http://localhost:" + port);
    }

    private static void handleHandoff(HttpExchange exchange, MarketplaceOperationsService service) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) { send(exchange, 405, Map.of("error", "method_not_allowed")); return; }
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            OrderHandoff input = new OrderHandoff(
                    required(body, "eventId"), required(body, "orderId"), required(body, "sellerId"),
                    required(body, "buyerId"), required(body, "assetId"),
                    Json.booleanField(body, "sellerAssetReady"), Json.booleanField(body, "buyerConfirmed"),
                    Long.parseLong(required(body, "amountMinor")), required(body, "currency"),
                    Json.stringField(body, "occurredAt") == null ? Instant.now().toString() : Json.stringField(body, "occurredAt"));
            send(exchange, 202, service.record(input));
        } catch (IllegalArgumentException error) {
            send(exchange, 400, Map.of("error", "invalid_request", "message", error.getMessage()));
        } catch (InfraiClient.InfraiException error) {
            int status = error.status() >= 400 && error.status() < 500 ? error.status() : 502;
            send(exchange, status, Map.of("error", error.code()));
        } catch (Exception error) {
            send(exchange, 502, Map.of("error", "delivery_failed"));
        }
    }

    private static void handleToken(HttpExchange exchange, MarketplaceOperationsService service) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) { send(exchange, 405, Map.of("error", "method_not_allowed")); return; }
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            sendRaw(exchange, 200, service.dashboardToken(required(body, "clientId")));
        } catch (IllegalArgumentException error) {
            send(exchange, 400, Map.of("error", "invalid_request", "message", error.getMessage()));
        } catch (InfraiClient.InfraiException error) {
            int status = error.status() >= 400 && error.status() < 500 ? error.status() : 502;
            send(exchange, status, Map.of("error", error.code()));
        } catch (Exception error) {
            send(exchange, 502, Map.of("error", "token_delivery_failed"));
        }
    }

    private static String required(String body, String field) {
        String text = Json.stringField(body, field);
        if (text != null) return text;
        String marker = "\"" + field + "\":";
        int start = body.indexOf(marker);
        if (start >= 0) {
            start += marker.length();
            int end = start;
            while (end < body.length() && Character.isDigit(body.charAt(end))) end++;
            if (end > start) return body.substring(start, end);
        }
        throw new IllegalArgumentException(field + " is required");
    }

    private static void send(HttpExchange exchange, int status, Object value) throws IOException {
        sendRaw(exchange, status, Json.write(value));
    }

    private static void sendRaw(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
