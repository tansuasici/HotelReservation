package hotel.reservation.message;

/**
 * Message type constants for Contract Net Protocol communication.
 */
public final class MessageTypes {

    private MessageTypes() {} // Prevent instantiation

    // Contract Net Protocol Messages
    public static final String MSG_CFP = "CFP";                 // Call For Proposals
    public static final String MSG_PROPOSAL = "Proposal";       // Hotel's proposal
    public static final String MSG_REFUSE = "Refuse";           // Hotel refuses to bid
    public static final String MSG_ACCEPT = "Accept";           // Customer accepts proposal
    public static final String MSG_REJECT = "Reject";           // Customer rejects proposal
    public static final String MSG_CONFIRM = "Confirm";         // Hotel confirms reservation

    // Negotiation Messages
    public static final String MSG_NEGOTIATE_START = "NegotiateStart";
    public static final String MSG_COUNTER_OFFER = "CounterOffer";
    public static final String MSG_NEGOTIATE_ACCEPT = "NegotiateAccept";
    public static final String MSG_NEGOTIATE_REJECT = "NegotiateReject";
}
