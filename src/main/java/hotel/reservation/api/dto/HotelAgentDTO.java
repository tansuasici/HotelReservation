package hotel.reservation.api.dto;

import hotel.reservation.agent.HotelAgent;

/**
 * DTO for hotel agent.
 */
public class HotelAgentDTO {
    private String agentName;
    private String hotelId;
    private String hotelName;
    private String location;
    private int rank;
    private double basePrice;

    public HotelAgentDTO() {}

    public static HotelAgentDTO from(HotelAgent agent) {
        HotelAgentDTO dto = new HotelAgentDTO();
        dto.agentName = agent.getName();
        dto.hotelId = agent.getHotelId();
        dto.hotelName = agent.getHotelName();
        dto.location = agent.getLocation();
        dto.rank = agent.getRank();
        dto.basePrice = agent.getBasePrice();
        return dto;
    }

    // Getters and Setters
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

    public String getHotelId() { return hotelId; }
    public void setHotelId(String hotelId) { this.hotelId = hotelId; }

    public String getHotelName() { return hotelName; }
    public void setHotelName(String hotelName) { this.hotelName = hotelName; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public double getBasePrice() { return basePrice; }
    public void setBasePrice(double basePrice) { this.basePrice = basePrice; }
}
