package hotel.reservation.role.pricing;

/**
 * Strategy interface for seller (hotel) pricing during negotiation.
 * Implementations define how the hotel adjusts counter-offers across rounds.
 */
@FunctionalInterface
public interface SellerPricingStrategy {

    /**
     * Calculate a counter-offer price for the seller.
     *
     * @param basePrice   the hotel's listed price
     * @param minPrice    the hotel's minimum acceptable price
     * @param flexibility how flexible the hotel is (0.0-1.0)
     * @param round       current negotiation round (1-based)
     * @param maxRounds   total negotiation rounds allowed
     * @return counter-offer price
     */
    double counterOffer(double basePrice, double minPrice, double flexibility, int round, int maxRounds);
}
