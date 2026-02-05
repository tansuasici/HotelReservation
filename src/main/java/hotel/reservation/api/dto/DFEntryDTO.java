package hotel.reservation.api.dto;

import hotel.reservation.df.DFEntry;

/**
 * DTO for Directory Facilitator entries.
 */
public class DFEntryDTO {
    private String agentId;
    private String agentName;
    private String hotelId;
    private String hotelName;
    private String location;
    private int rank;
    private double basePrice;
    private boolean available;

    public DFEntryDTO() {}

    public static DFEntryDTO from(DFEntry entry) {
        DFEntryDTO dto = new DFEntryDTO();
        dto.agentId = entry.getAgentId();
        dto.agentName = entry.getAgentName();
        dto.hotelId = entry.getHotelId();
        dto.hotelName = entry.getHotelName();
        dto.location = entry.getLocation();
        dto.rank = entry.getRank();
        dto.basePrice = entry.getBasePrice();
        dto.available = entry.isAvailable();
        return dto;
    }

    // Getters and Setters
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

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

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}
