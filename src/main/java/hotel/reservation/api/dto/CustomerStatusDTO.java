package hotel.reservation.api.dto;

import hotel.reservation.agent.CustomerAgent;
import hotel.reservation.message.ReservationConfirmation;
import hotel.reservation.message.RoomProposal;
import hotel.reservation.role.CustomerRole;

/**
 * DTO for customer status.
 */
public class CustomerStatusDTO {
    private String customerId;
    private String state;
    private String desiredLocation;
    private int desiredRank;
    private double maxPrice;
    private int proposalCount;
    private ProposalDTO selectedProposal;
    private ConfirmationDTO confirmation;

    public CustomerStatusDTO() {}

    public static CustomerStatusDTO from(CustomerAgent customer, CustomerRole role) {
        CustomerStatusDTO dto = new CustomerStatusDTO();
        dto.customerId = customer.getName();
        dto.desiredLocation = customer.getDesiredLocation();
        dto.desiredRank = customer.getDesiredRank();
        dto.maxPrice = customer.getMaxPrice();

        if (role != null) {
            dto.state = role.getCustomerState().name();
            dto.proposalCount = role.getProposals().size();

            RoomProposal selected = role.getSelectedProposal();
            if (selected != null) {
                dto.selectedProposal = ProposalDTO.from(selected);
            }

            ReservationConfirmation conf = role.getConfirmation();
            if (conf != null) {
                dto.confirmation = ConfirmationDTO.from(conf);
            }
        }

        return dto;
    }

    // Getters and Setters
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getDesiredLocation() { return desiredLocation; }
    public void setDesiredLocation(String desiredLocation) { this.desiredLocation = desiredLocation; }

    public int getDesiredRank() { return desiredRank; }
    public void setDesiredRank(int desiredRank) { this.desiredRank = desiredRank; }

    public double getMaxPrice() { return maxPrice; }
    public void setMaxPrice(double maxPrice) { this.maxPrice = maxPrice; }

    public int getProposalCount() { return proposalCount; }
    public void setProposalCount(int proposalCount) { this.proposalCount = proposalCount; }

    public ProposalDTO getSelectedProposal() { return selectedProposal; }
    public void setSelectedProposal(ProposalDTO selectedProposal) { this.selectedProposal = selectedProposal; }

    public ConfirmationDTO getConfirmation() { return confirmation; }
    public void setConfirmation(ConfirmationDTO confirmation) { this.confirmation = confirmation; }
}
