package hotel.reservation.role.pricing;

/**
 * Strategy interface for buyer (customer) pricing during negotiation.
 * Implementations define how the customer adjusts offers across rounds.
 */
@FunctionalInterface
public interface BuyerPricingStrategy {

    /**
     * Calculate a counter-offer price for the buyer.
     *
     * @param desiredPrice the buyer's ideal price
     * @param maxPrice     the buyer's maximum acceptable price
     * @param round        current negotiation round (1-based)
     * @param maxRounds    total negotiation rounds allowed
     * @return counter-offer price
     */
    double counterOffer(double desiredPrice, double maxPrice, int round, int maxRounds);
}
