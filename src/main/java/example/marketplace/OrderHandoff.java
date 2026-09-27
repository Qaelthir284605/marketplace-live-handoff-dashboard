package example.marketplace;

record OrderHandoff(
        String eventId,
        String orderId,
        String sellerId,
        String buyerId,
        String assetId,
        boolean sellerAssetReady,
        boolean buyerConfirmed,
        long amountMinor,
        String currency,
        String occurredAt) {}

record HandoffSnapshot(
        String eventId,
        String orderId,
        String sellerId,
        String buyerId,
        String assetId,
        long amountMinor,
        String currency,
        String state,
        String occurredAt) {}

final class HandoffPolicy {
    private HandoffPolicy() {}

    static HandoffSnapshot decide(OrderHandoff handoff) {
        requireText(handoff.eventId(), "eventId");
        requireText(handoff.orderId(), "orderId");
        requireText(handoff.sellerId(), "sellerId");
        requireText(handoff.buyerId(), "buyerId");
        requireText(handoff.assetId(), "assetId");
        requireText(handoff.currency(), "currency");
        requireText(handoff.occurredAt(), "occurredAt");
        if (handoff.amountMinor() <= 0) throw new IllegalArgumentException("amountMinor must be positive");

        String state = handoff.sellerAssetReady() && handoff.buyerConfirmed()
                ? "READY_FOR_BUYER" : "AWAITING_CONFIRMATION";
        return new HandoffSnapshot(handoff.eventId(), handoff.orderId(), handoff.sellerId(), handoff.buyerId(),
                handoff.assetId(), handoff.amountMinor(), handoff.currency(), state, handoff.occurredAt());
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
