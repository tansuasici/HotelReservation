package hotel.reservation.role;

/**
 * Pure pricing functions for CNP negotiation.
 * Used by both CustomerRole (buyer) and HotelProviderRole (seller).
 */
public final class NegotiationPricing {

    private NegotiationPricing() {}

    /**
     * Customer counter-offer: starts at desiredPrice, linearly approaches maxPrice as rounds progress.
     */
    public static double customerCounterOffer(double desiredPrice, double maxPrice, int round, int maxRounds) {
        double progress = (double) round / maxRounds;
        return desiredPrice + (maxPrice - desiredPrice) * progress;
    }

    /**
     * Hotel counter-offer: starts at basePrice, reduces towards minPrice based on flexibility.
     * Higher flexibility = faster price reduction.
     */
    public static double hotelCounterOffer(double basePrice, double minPrice, double flexibility, int round, int maxRounds) {
        double progress = (double) round / maxRounds;
        double reduction = (basePrice - minPrice) * progress * flexibility;
        return Math.max(basePrice - reduction, minPrice);
    }
}
