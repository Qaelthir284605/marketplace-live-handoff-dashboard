package example.marketplace;

public final class HandoffPolicyTest {
    public static void main(String[] args) {
        OrderHandoff complete = new OrderHandoff("evt-71", "order-2048", "seller-19", "buyer-52", "asset-8",
                true, true, 12500, "USD", "2026-09-25T08:00:00Z");
        HandoffSnapshot ready = HandoffPolicy.decide(complete);
        check("READY_FOR_BUYER".equals(ready.state()), "complete handoff must be ready");

        OrderHandoff pendingBuyer = new OrderHandoff("evt-72", "order-2049", "seller-19", "buyer-53", "asset-9",
                true, false, 6400, "USD", "2026-09-25T08:01:00Z");
        HandoffSnapshot pending = HandoffPolicy.decide(pendingBuyer);
        check("AWAITING_CONFIRMATION".equals(pending.state()), "missing buyer confirmation must remain pending");
        check(pending.orderId().equals("order-2049"), "audit identity must survive the decision");
        System.out.println("HandoffPolicyTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
