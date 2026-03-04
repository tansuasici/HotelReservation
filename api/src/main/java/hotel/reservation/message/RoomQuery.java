package hotel.reservation.message;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * CFP (Call For Proposals) message payload.
 * Sent by CustomerAgent to HotelAgents to request room proposals.
 */
public class RoomQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    private String customerId;
    private String location;      // Desired city
    private int minRank;          // Minimum star rating
    private double maxPrice;      // Maximum price per night
    private int numberOfRooms;    // Number of rooms requested
    private List<String> amenities; // Required amenities (e.g., "wifi", "breakfast")
    private String checkInDate;   // Optional: check-in date
    private String checkOutDate;  // Optional: check-out date
    private int numberOfNights;   // Number of nights
    private long timestamp;       // Query timestamp

    public RoomQuery() {
        this.timestamp = System.currentTimeMillis();
        this.numberOfNights = 1;
        this.numberOfRooms = 1;
        this.amenities = Collections.emptyList();
    }

    public RoomQuery(String customerId, String location, int minRank, double maxPrice) {
        this();
        this.customerId = customerId;
        this.location = location;
        this.minRank = minRank;
        this.maxPrice = maxPrice;
    }

    // Getters and Setters
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public int getMinRank() { return minRank; }
    public void setMinRank(int minRank) { this.minRank = minRank; }

    public double getMaxPrice() { return maxPrice; }
    public void setMaxPrice(double maxPrice) { this.maxPrice = maxPrice; }

    public int getNumberOfRooms() { return numberOfRooms; }
    public void setNumberOfRooms(int numberOfRooms) { this.numberOfRooms = numberOfRooms; }

    public List<String> getAmenities() { return amenities; }
    public void setAmenities(List<String> amenities) { this.amenities = amenities != null ? amenities : Collections.emptyList(); }

    public String getCheckInDate() { return checkInDate; }
    public void setCheckInDate(String checkInDate) { this.checkInDate = checkInDate; }

    public String getCheckOutDate() { return checkOutDate; }
    public void setCheckOutDate(String checkOutDate) { this.checkOutDate = checkOutDate; }

    public int getNumberOfNights() { return numberOfNights; }
    public void setNumberOfNights(int numberOfNights) { this.numberOfNights = numberOfNights; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return String.format("RoomQuery[customer=%s, location=%s, minRank=%d, maxPrice=%.2f, rooms=%d, amenities=%s]",
            customerId, location, minRank, maxPrice, numberOfRooms, amenities);
    }
}
