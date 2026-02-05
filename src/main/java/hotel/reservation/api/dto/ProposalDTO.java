package hotel.reservation.api.dto;

import hotel.reservation.message.RoomProposal;

import java.util.List;

/**
 * DTO for room proposal.
 */
public class ProposalDTO {
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
    private long timestamp;

    public ProposalDTO() {}

    public static ProposalDTO from(RoomProposal proposal) {
        ProposalDTO dto = new ProposalDTO();
        dto.proposalId = proposal.getProposalId();
        dto.hotelId = proposal.getHotelId();
        dto.hotelName = proposal.getHotelName();
        dto.location = proposal.getLocation();
        dto.rank = proposal.getRank();
        dto.pricePerNight = proposal.getPricePerNight();
        dto.currency = proposal.getCurrency();
        dto.roomType = proposal.getRoomType();
        dto.amenities = proposal.getAmenities();
        dto.rating = proposal.getRating();
        dto.timestamp = proposal.getTimestamp();
        return dto;
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
}
