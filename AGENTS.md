# Agents Documentation

This document describes the agents in the Hotel Reservation Multi-Agent System, their roles, behaviors, and interactions.

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    HotelReservationPlayground                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────────┐    ┌──────────────────────┐          │
│  │  DirectoryFacilitator │    │      HotelEnv        │          │
│  │        (DF)           │    │    (Environment)     │          │
│  │  - Yellow Pages       │    │                      │          │
│  │  - Agent Registry     │    │                      │          │
│  └──────────────────────┘    └──────────────────────┘          │
│                                                                  │
│  ┌──────────────────────┐    ┌──────────────────────┐          │
│  │    CustomerAgent      │    │     HotelAgent       │ (x7)     │
│  │  ┌────────────────┐  │    │  ┌────────────────┐  │          │
│  │  │  CustomerRole  │  │    │  │HotelProviderRole│  │          │
│  │  │  - startSearch │  │    │  │ - handleCFP    │  │          │
│  │  │  - evaluate    │  │    │  │ - sendProposal │  │          │
│  │  │  - reserve     │  │    │  │ - confirm      │  │          │
│  │  └────────────────┘  │    │  └────────────────┘  │          │
│  └──────────────────────┘    └──────────────────────┘          │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 1. CustomerAgent

**Purpose:** Represents a customer looking for hotel rooms. Initiates the Contract Net Protocol.

**File:** `src/main/java/hotel/reservation/agent/CustomerAgent.java`

### Properties
| Property | Type | Description |
|----------|------|-------------|
| desiredLocation | String | Target city (e.g., "Istanbul") |
| desiredRank | int | Minimum star rating (1-5) |
| maxPrice | double | Maximum budget per night |
| desiredPrice | double | Target price for negotiation |

### Initialization
```java
CustomerAgent customer = new CustomerAgent(
    "Customer-1",    // name
    "Istanbul",      // desiredLocation
    5,               // desiredRank (5-star)
    500              // maxPrice ($500/night)
);
```

### Lifecycle
1. `setup()` - Adopts CustomerRole, registers capabilities
2. `startSearch()` - Triggers hotel search via CustomerRole

### Role: CustomerRole

**File:** `src/main/java/hotel/reservation/role/CustomerRole.java`

#### State Machine
```
IDLE → SEARCHING → WAITING_PROPOSALS → EVALUATING → RESERVING → COMPLETED
                                                              ↓
                                                           FAILED
```

#### Key Methods
| Method | Description |
|--------|-------------|
| `startSearch()` | Query DF, broadcast CFP to matching hotels |
| `handleProposalMessage(Message<RoomProposal>)` | Collect incoming proposals |
| `handleRefuseMessage(Message<String>)` | Handle hotel refusals |
| `evaluateProposals()` | Select best proposal (lowest price, FCFS) |
| `makeReservation()` | Send Accept to winner, Reject to others |
| `handleConfirmMessage(Message<ReservationConfirmation>)` | Process confirmation |

#### Selection Algorithm
```java
// Sort by price ascending, then by timestamp (FCFS for ties)
proposals.sort(Comparator
    .comparingDouble(RoomProposal::getPricePerNight)
    .thenComparingLong(RoomProposal::getTimestamp));
return proposals.get(0);  // Select first (cheapest)
```

---

## 2. HotelAgent

**Purpose:** Represents a hotel in the system. Participates in the Contract Net Protocol by responding to CFP messages.

**File:** `src/main/java/hotel/reservation/agent/HotelAgent.java`

### Properties
| Property | Type | Description |
|----------|------|-------------|
| hotelId | String | Unique identifier (e.g., "h001") |
| hotelName | String | Display name (e.g., "Grand Istanbul Hotel") |
| location | String | City where hotel is located |
| rank | int | Star rating (1-5) |
| basePrice | double | Price per night |

### Initialization
```java
HotelAgent hotel = new HotelAgent(
    "h001",                  // hotelId
    "Grand Istanbul Hotel",  // hotelName
    "Istanbul",              // location
    5,                       // rank (5-star)
    450                      // basePrice ($450/night)
);
```

### Lifecycle
1. `setup()` - Adopts HotelProviderRole
2. `registerWithDF()` - Registers with Directory Facilitator
3. Waits for CFP messages

### Role: HotelProviderRole

**File:** `src/main/java/hotel/reservation/role/HotelProviderRole.java`

#### Behavior Parameters
| Parameter | Default | Description |
|-----------|---------|-------------|
| responseRate | 0.8 | 80% chance to respond (simulates availability) |
| available | true | Hotel availability status |

#### Key Methods
| Method | Description |
|--------|-------------|
| `handleCFPMessage(Message<RoomQuery>)` | Process CFP, decide to respond or refuse |
| `matchesQuery(RoomQuery)` | Check if hotel matches criteria |
| `shouldRespond()` | Random decision to simulate real availability |
| `sendProposal(Identifier)` | Create and send RoomProposal |
| `sendRefusal(Identifier, String)` | Send refusal with reason |
| `handleAcceptMessage(Message<ReservationRequest>)` | Process acceptance, confirm reservation |
| `handleRejectMessage(Message<String>)` | Handle rejection, clean up |

#### Decision Flow
```
CFP Received
    │
    ▼
matchesQuery()?
    │
    ├── No ──→ Ignore (don't respond)
    │
    ▼ Yes
shouldRespond()?
    │
    ├── No ──→ Send Refuse message
    │
    ▼ Yes
Send Proposal
```

---

## 3. DirectoryFacilitator (DF)

**Purpose:** Acts as a yellow pages service. Hotels register their services, and customers search for available hotels.

**File:** `src/main/java/hotel/reservation/df/DirectoryFacilitator.java`

**Type:** Extends `Environment` (not a regular Agent)

### Registry
Maintains a `Map<String, DFEntry>` of registered hotel agents.

### Key Methods
| Method | Description |
|--------|-------------|
| `register(DFEntry)` | Register a hotel agent |
| `deregister(String agentId)` | Remove registration |
| `search(location, minRank, maxPrice)` | Find matching hotels |
| `getAllEntries()` | List all registrations |
| `getAvailableLocations()` | Get unique cities |

### DFEntry Structure
```java
public class DFEntry {
    String agentId;        // Agent identifier
    String agentName;      // Agent name
    String serviceType;    // "hotel-provider"
    String hotelId;        // Hotel ID
    String hotelName;      // Hotel name
    String location;       // City
    int rank;              // Star rating
    double basePrice;      // Price per night
    boolean available;     // Availability
    long registrationTime; // When registered
}
```

---

## 4. Communication Flow

### Message Types

```java
public class MessageTypes {
    // Contract Net Protocol
    public static final String MSG_CFP = "CFP";
    public static final String MSG_PROPOSAL = "Proposal";
    public static final String MSG_REFUSE = "Refuse";
    public static final String MSG_ACCEPT = "Accept";
    public static final String MSG_REJECT = "Reject";
    public static final String MSG_CONFIRM = "Confirm";

    // Directory Facilitator
    public static final String MSG_DF_REGISTER = "DFRegister";
    public static final String MSG_DF_SEARCH = "DFSearch";
    public static final String MSG_DF_RESULT = "DFResult";
}
```

### Complete Interaction Sequence

```
┌──────────┐         ┌────┐         ┌─────────┐
│ Customer │         │ DF │         │ Hotels  │
└────┬─────┘         └──┬─┘         └────┬────┘
     │                  │                 │
     │  1. Query DF     │                 │
     │─────────────────>│                 │
     │                  │                 │
     │  2. Return matching hotels         │
     │<─────────────────│                 │
     │                  │                 │
     │  3. CFP (Call For Proposals)       │
     │───────────────────────────────────>│
     │                  │                 │
     │  4a. Proposal                      │
     │<───────────────────────────────────│
     │  4b. Refuse                        │
     │<───────────────────────────────────│
     │                  │                 │
     │  [Wait for deadline or all responses]
     │                  │                 │
     │  5. Evaluate proposals             │
     │  (Select lowest price, FCFS)       │
     │                  │                 │
     │  6a. Accept (to selected hotel)    │
     │───────────────────────────────────>│
     │  6b. Reject (to other hotels)      │
     │───────────────────────────────────>│
     │                  │                 │
     │  7. Confirm                        │
     │<───────────────────────────────────│
     │                  │                 │
     ▼                  ▼                 ▼
```

---

## 5. Creating Custom Agents

### Adding a New Hotel Agent

```java
// In HotelReservationPlayground.setup()
create(new HotelAgent(
    "h008",                    // hotelId
    "My New Hotel",            // hotelName
    "Bursa",                   // location
    4,                         // rank
    200                        // basePrice
));
```

### Adding a New Customer Agent

```java
// In HotelReservationPlayground.setup()
create(new CustomerAgent(
    "Customer-2",              // name
    "Antalya",                 // desiredLocation
    4,                         // desiredRank
    400                        // maxPrice
));
```

### Via REST API

```bash
# Add hotel at runtime
curl -X POST "http://localhost:8080/api/hotels" \
  -H "Content-Type: application/json" \
  -d '{
    "hotelId": "h008",
    "hotelName": "My New Hotel",
    "location": "Bursa",
    "rank": 4,
    "basePrice": 200
  }'
```

---

## 6. Agent Configuration

### Default Agents Created in Playground

| Agent Type | Name | Details |
|------------|------|---------|
| Customer | Customer-1 | Istanbul, 5-star, max $500 |
| Hotel | Hotel-h001 | Grand Istanbul Hotel, 5-star, $450 |
| Hotel | Hotel-h002 | Luxury Palace Istanbul, 5-star, $400 |
| Hotel | Hotel-h003 | Budget Inn Istanbul, 3-star, $150 |
| Hotel | Hotel-h004 | Sea View Resort (Izmir), 4-star, $300 |
| Hotel | Hotel-h005 | Ankara Business Hotel, 4-star, $250 |
| Hotel | Hotel-h006 | Cappadocia Cave Hotel, 5-star, $350 |
| Hotel | Hotel-h007 | Antalya Beach Resort, 5-star, $500 |

### Expected CNP Result for Default Setup

Customer-1 searches for 5-star hotels in Istanbul with max $500:
- **Matching hotels:** h001 ($450), h002 ($400)
- **Expected winner:** h002 (Luxury Palace) - lowest price at $400
- **Result:** Reservation confirmed with Luxury Palace Istanbul

---

## 7. Extending Agent Behavior

### Adding Weather-Aware Search

```java
// In CustomerRole, before startSearch():
public void startSmartSearch() {
    // 1. Get weather via REST API
    WeatherDTO weather = fetchWeather(desiredLocation);

    // 2. Adjust preferences
    if (weather.getMain().getTemp() > 25) {
        preferredAmenities.add("pool");
    }

    // 3. Continue with search
    startSearch();
}
```

### Adding Negotiation (Future)

```java
// In CustomerRole, after evaluateProposals():
if (selectedProposal.getPricePerNight() > desiredPrice) {
    // Start negotiation
    startNegotiation(selectedProposal);
}
```
