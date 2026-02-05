package hotel.reservation.df;

import java.io.Serializable;

/**
 * Directory Facilitator registration entry.
 * Represents a hotel agent's registration in the DF.
 */
public class DFEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private String agentId;
    private String agentName;
    private String serviceType;    // "hotel-provider"
    private String hotelId;
    private String hotelName;
    private String location;       // City
    private int rank;              // Star rating
    private double basePrice;
    private boolean available;
    private long registrationTime;

    public DFEntry() {
        this.registrationTime = System.currentTimeMillis();
        this.serviceType = "hotel-provider";
        this.available = true;
    }

    public DFEntry(String agentId, String agentName, String hotelId, String hotelName,
                   String location, int rank, double basePrice) {
        this();
        this.agentId = agentId;
        this.agentName = agentName;
        this.hotelId = hotelId;
        this.hotelName = hotelName;
        this.location = location;
        this.rank = rank;
        this.basePrice = basePrice;
    }

    /**
     * Check if this entry matches the search criteria.
     */
    public boolean matches(String searchLocation, Integer minRank, Double maxPrice) {
        if (!available) return false;

        if (searchLocation != null && !searchLocation.equalsIgnoreCase(location)) {
            return false;
        }
        if (minRank != null && rank < minRank) {
            return false;
        }
        if (maxPrice != null && basePrice > maxPrice) {
            return false;
        }
        return true;
    }

    // Getters and Setters
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }

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

    public long getRegistrationTime() { return registrationTime; }
    public void setRegistrationTime(long registrationTime) { this.registrationTime = registrationTime; }

    @Override
    public String toString() {
        return String.format("DFEntry[%s] %s - %s - %d star - $%.2f",
            agentId, hotelName, location, rank, basePrice);
    }
}
