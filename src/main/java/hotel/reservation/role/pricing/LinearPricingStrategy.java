package hotel.reservation.role.pricing;

/**
 * Default linear pricing strategy for both buyer and seller.
 * Buyer: linearly increases offer from desiredPrice towards maxPrice.
 * Seller: linearly reduces price from basePrice towards minPrice based on flexibility.
 */
public class LinearPricingStrategy implements BuyerPricingStrategy, SellerPricingStrategy {

    @Override
    public double counterOffer(double desiredPrice, double maxPrice, int round, int maxRounds) {
        double progress = (double) round / maxRounds;
        return desiredPrice + (maxPrice - desiredPrice) * progress;
    }

    @Override
    public double counterOffer(double basePrice, double minPrice, double flexibility, int round, int maxRounds) {
        double progress = (double) round / maxRounds;
        double reduction = (basePrice - minPrice) * progress * flexibility;
        return Math.max(basePrice - reduction, minPrice);
    }
}
