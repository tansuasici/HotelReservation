package hotel.reservation.api.dto;

/**
 * DTO for simulation status.
 */
public class SimulationStatusDTO {
    private String state;
    private long currentTick;
    private int agentCount;
    private int registeredHotels;
    private String message;

    public SimulationStatusDTO() {}

    public SimulationStatusDTO(String state, long currentTick, int agentCount,
                                int registeredHotels, String message) {
        this.state = state;
        this.currentTick = currentTick;
        this.agentCount = agentCount;
        this.registeredHotels = registeredHotels;
        this.message = message;
    }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public long getCurrentTick() { return currentTick; }
    public void setCurrentTick(long currentTick) { this.currentTick = currentTick; }

    public int getAgentCount() { return agentCount; }
    public void setAgentCount(int agentCount) { this.agentCount = agentCount; }

    public int getRegisteredHotels() { return registeredHotels; }
    public void setRegisteredHotels(int registeredHotels) { this.registeredHotels = registeredHotels; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
