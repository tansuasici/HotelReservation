package hotel.reservation.message;

import java.io.Serializable;

/**
 * Negotiation message payload.
 * Used for price bargaining between Customer and Hotel agents.
 */
public class NegotiationOffer implements Serializable {

    private static final long serialVersionUID = 1L;

    private String proposalId;
    private String hotelId;
    private String hotelName;
    private double offeredPrice;
    private double originalPrice;
    private int round;
    private int maxRounds;
    private String message;
    private long timestamp;

    public NegotiationOffer() {
        this.timestamp = System.currentTimeMillis();
    }

    public NegotiationOffer(String proposalId, String hotelId, String hotelName,
                            double offeredPrice, double originalPrice,
                            int round, int maxRounds, String message) {
        this();
        this.proposalId = proposalId;
        this.hotelId = hotelId;
        this.hotelName = hotelName;
        this.offeredPrice = offeredPrice;
        this.originalPrice = originalPrice;
        this.round = round;
        this.maxRounds = maxRounds;
        this.message = message;
    }

    // Getters and Setters
    public String getProposalId() { return proposalId; }
    public void setProposalId(String proposalId) { this.proposalId = proposalId; }

    public String getHotelId() { return hotelId; }
    public void setHotelId(String hotelId) { this.hotelId = hotelId; }

    public String getHotelName() { return hotelName; }
    public void setHotelName(String hotelName) { this.hotelName = hotelName; }

    public double getOfferedPrice() { return offeredPrice; }
    public void setOfferedPrice(double offeredPrice) { this.offeredPrice = offeredPrice; }

    public double getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(double originalPrice) { this.originalPrice = originalPrice; }

    public int getRound() { return round; }
    public void setRound(int round) { this.round = round; }

    public int getMaxRounds() { return maxRounds; }
    public void setMaxRounds(int maxRounds) { this.maxRounds = maxRounds; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return String.format("NegotiationOffer[proposal=%s, hotel=%s, offered=$%.2f, original=$%.2f, round=%d/%d]",
            proposalId, hotelName, offeredPrice, originalPrice, round, maxRounds);
    }
}
