package hotel.reservation.api.dto;

import hotel.reservation.message.NegotiationOffer;

import java.util.List;
import java.util.stream.Collectors;

/**
 * DTO for negotiation history and status.
 */
public class NegotiationDTO {
    private String proposalId;
    private String hotelId;
    private String hotelName;
    private double offeredPrice;
    private double originalPrice;
    private int round;
    private int maxRounds;
    private String message;
    private long timestamp;

    public NegotiationDTO() {}

    public static NegotiationDTO from(NegotiationOffer offer) {
        NegotiationDTO dto = new NegotiationDTO();
        dto.proposalId = offer.getProposalId();
        dto.hotelId = offer.getHotelId();
        dto.hotelName = offer.getHotelName();
        dto.offeredPrice = offer.getOfferedPrice();
        dto.originalPrice = offer.getOriginalPrice();
        dto.round = offer.getRound();
        dto.maxRounds = offer.getMaxRounds();
        dto.message = offer.getMessage();
        dto.timestamp = offer.getTimestamp();
        return dto;
    }

    public static List<NegotiationDTO> fromList(List<NegotiationOffer> offers) {
        return offers.stream()
            .map(NegotiationDTO::from)
            .collect(Collectors.toList());
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
}
