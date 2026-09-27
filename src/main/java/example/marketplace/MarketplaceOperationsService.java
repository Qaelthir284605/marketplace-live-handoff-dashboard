package example.marketplace;

final class MarketplaceOperationsService {
    private final InfraiClient infrai;

    MarketplaceOperationsService(InfraiClient infrai) { this.infrai = infrai; }

    HandoffSnapshot record(OrderHandoff handoff) throws Exception {
        HandoffSnapshot snapshot = HandoffPolicy.decide(handoff);
        infrai.batchMetrics(snapshot);
        infrai.publish(snapshot);
        return snapshot;
    }

    String dashboardToken(String clientId) throws Exception {
        if (clientId == null || clientId.isBlank()) throw new IllegalArgumentException("clientId is required");
        return infrai.issueDashboardToken(clientId);
    }
}
