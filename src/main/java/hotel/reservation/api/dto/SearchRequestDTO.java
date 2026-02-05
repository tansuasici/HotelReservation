package hotel.reservation.api.dto;

/**
 * DTO for search request.
 */
public class SearchRequestDTO {
    private String customerId;
    private String location;
    private Integer minRank;
    private Double maxPrice;

    public SearchRequestDTO() {}

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Integer getMinRank() { return minRank; }
    public void setMinRank(Integer minRank) { this.minRank = minRank; }

    public Double getMaxPrice() { return maxPrice; }
    public void setMaxPrice(Double maxPrice) { this.maxPrice = maxPrice; }

    @Override
    public String toString() {
        return String.format("SearchRequest[customer=%s, location=%s, minRank=%d, maxPrice=%.2f]",
            customerId, location, minRank, maxPrice);
    }
}
