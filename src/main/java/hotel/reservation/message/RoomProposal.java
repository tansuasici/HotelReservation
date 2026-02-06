package hotel.reservation.message;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * Proposal message payload.
 * Sent by HotelAgent in response to a CFP.
 */
public class RoomProposal implements Serializable {

    private static final long serialVersionUID = 1L;

    private String proposalId;
    private String hotelId;
    private String hotelName;
    private String location;
    private int rank;
    private double pricePerNight;
    private String currency;
    private String roomType;
    private List<String> amenities;
    private double rating;
    private long timestamp;  // For FCFS ordering
    private Double negotiatedPrice;  // Price after negotiation (null if not negotiated)
    private boolean negotiated = false;  // Whether price was negotiated

    public RoomProposal() {
        this.timestamp = System.currentTimeMillis();
        this.currency = "USD";
    }

    public RoomProposal(String hotelId, String hotelName, String location, int rank,
                        double pricePerNight, String roomType) {
        this();
        this.proposalId = UUID.randomUUID().toString();
        this.hotelId = hotelId;
        this.hotelName = hotelName;
        this.location = location;
        this.rank = rank;
        this.pricePerNight = pricePerNight;
        this.roomType = roomType;
    }

    // Getters and Setters
    public String getProposalId() { return proposalId; }
    public void setProposalId(String proposalId) { this.proposalId = proposalId; }

    public String getHotelId() { return hotelId; }
    public void setHotelId(String hotelId) { this.hotelId = hotelId; }

    public String getHotelName() { return hotelName; }
    public void setHotelName(String hotelName) { this.hotelName = hotelName; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public double getPricePerNight() { return pricePerNight; }
    public void setPricePerNight(double pricePerNight) { this.pricePerNight = pricePerNight; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getRoomType() { return roomType; }
    public void setRoomType(String roomType) { this.roomType = roomType; }

    public List<String> getAmenities() { return amenities; }
    public void setAmenities(List<String> amenities) { this.amenities = amenities; }

    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public Double getNegotiatedPrice() { return negotiatedPrice; }
    public void setNegotiatedPrice(Double negotiatedPrice) { this.negotiatedPrice = negotiatedPrice; }

    public boolean isNegotiated() { return negotiated; }
    public void setNegotiated(boolean negotiated) { this.negotiated = negotiated; }

    /**
     * Get the effective price (negotiated if available, otherwise original).
     */
    public double getEffectivePrice() {
        return negotiated && negotiatedPrice != null ? negotiatedPrice : pricePerNight;
    }

    @Override
    public String toString() {
        return String.format("RoomProposal[%s] %s - %d star - $%.2f - %s",
            proposalId, hotelName, rank, pricePerNight, roomType);
    }
}
