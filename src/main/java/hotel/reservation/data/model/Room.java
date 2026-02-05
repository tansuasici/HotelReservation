package hotel.reservation.data.model;

import java.util.List;

/**
 * Room information within a hotel.
 */
public class Room {
    private String id;
    private String type;  // standard, deluxe, suite
    private double price;
    private int capacity;
    private List<String> amenities;
    private boolean available;

    public Room() {}

    public Room(String id, String type, double price, int capacity, boolean available) {
        this.id = id;
        this.type = type;
        this.price = price;
        this.capacity = capacity;
        this.available = available;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }

    public List<String> getAmenities() { return amenities; }
    public void setAmenities(List<String> amenities) { this.amenities = amenities; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    @Override
    public String toString() {
        return String.format("Room[%s] %s - $%.2f (capacity: %d)", id, type, price, capacity);
    }
}
