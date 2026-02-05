# Multi-Agent System: Hotel Room Reservation

## Scenario Overview

A **Customer Agent**, who wants to reserve hotel rooms on behalf of a user, asks for the appropriate rooms to the **Hotel Agents** representing real hotels in this scenario.

## System Architecture

### Agents

1. **Customer Agent**
   - Represents a user who wants to book a hotel room
   - Searches for Hotel Agents using the Directory Facilitator (DF)
   - Sends query messages to all discovered hotel agents
   - Receives proposals and makes reservation decisions
   - Query parameters include:
     - Location (e.g., Izmir, Istanbul, Ankara)
     - Hotel rank (e.g., 3-star, 4-star, 5-star)
     - Maximum price

2. **Hotel Agent(s)**
   - Represents real hotels in the system
   - Registers itself with the Directory Facilitator
   - Receives and processes room queries from Customer Agents
   - May or may not respond to queries (random decision)
   - Provides room proposals with pricing information
   - Attributes (given during initialization):
     - Hotel name
     - Location
     - Rank (star rating)
     - Room price

3. **Directory Facilitator (DF)**
   - Acts as a yellow pages service for agents
   - Hotel Agents register their services
   - Customer Agents search for available Hotel Agents

## Communication Protocol: Contract Net Protocol (CNP)

### Phase 1: Call for Proposals (CFP)
1. Customer Agent searches DF for Hotel Agents
2. Customer Agent broadcasts CFP to all discovered Hotel Agents
3. CFP contains: location, rank requirement, and price constraints

### Phase 2: Proposal Collection
1. Hotel Agents receive the CFP
2. Each Hotel Agent randomly decides whether to respond (simulating availability)
3. If responding, Hotel Agent sends a proposal with room details and price
4. **Deadline**: 30 seconds for responses

### Phase 3: Decision Making
1. If **no proposals** received: Reservation fails
2. If **one proposal** received: Automatic reservation with that hotel
3. If **multiple proposals** received:
   - Select based on **lowest price**
   - If multiple hotels have the same lowest price: **First Come First Served (FCFS)**

### Phase 4: Contract Award
1. Customer Agent sends acceptance to the selected Hotel Agent
2. Customer Agent sends rejection to other Hotel Agents
3. Selected Hotel Agent confirms the reservation

## Message Types

| Message Type | Sender | Receiver | Payload |
|-------------|--------|----------|---------|
| `CFP` (Call For Proposals) | Customer | Hotels | RoomQuery (location, rank, maxPrice) |
| `Proposal` | Hotel | Customer | RoomProposal (hotelName, location, rank, price, roomDetails) |
| `Refuse` | Hotel | Customer | RefusalReason |
| `Accept` | Customer | Hotel | ReservationRequest |
| `Reject` | Customer | Hotel | RejectionNotice |
| `Confirm` | Hotel | Customer | ReservationConfirmation |

## Bonus Feature: Negotiation Mechanism

After receiving proposals, if the lowest price is still above the Customer Agent's desired price:

1. **Negotiation Initiation**: Customer Agent starts negotiation with the Hotel Agent offering the lowest price
2. **Counter-Offer**: Customer Agent proposes a lower price
3. **Hotel Response**: Hotel Agent may:
   - Accept the counter-offer
   - Make a counter-counter-offer (closer to initial price)
   - Reject negotiation (final price stands)
4. **Convergence**: Negotiation continues until:
   - Agreement is reached
   - Maximum negotiation rounds exceeded
   - One party refuses to continue

### Negotiation Strategy
- **Customer Agent**: Tries to get the lowest possible price
- **Hotel Agent**: Tries to keep the price as close as possible to the initial value
- **Concession Rate**: Each party can have a configurable concession rate

## Technical Implementation

### Framework
- **SCOP Framework**: Main agent-based modeling framework
- **TNSAI Integration**: For intelligent agent behavior and decision making

### Configuration Parameters
- `TIMEOUT_TICK`: Simulation timeout
- `PROPOSAL_DEADLINE`: Time limit for hotel responses (30 seconds)
- `MAX_NEGOTIATION_ROUNDS`: Maximum rounds for price negotiation
- `DF_REFRESH_INTERVAL`: How often to refresh DF listings

### Agent Initialization Example

```
CustomerAgent:
  - name: "Customer-1"
  - desiredLocation: "Istanbul"
  - desiredRank: 5
  - maxPrice: 500

HotelAgent-1:
  - name: "Grand Hotel"
  - location: "Istanbul"
  - rank: 5
  - price: 450

HotelAgent-2:
  - name: "Luxury Palace"
  - location: "Istanbul"
  - rank: 5
  - price: 400

HotelAgent-3:
  - name: "Budget Inn"
  - location: "Istanbul"
  - rank: 3
  - price: 150
```

## External Services Integration

### 1. Hotel Data API (Dummy/Mock API)
Internal REST API that provides hotel information:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/hotels` | GET | List all hotels |
| `/api/hotels/{id}` | GET | Get hotel details |
| `/api/hotels/search` | GET | Search by location, rank, price |
| `/api/hotels/{id}/rooms` | GET | Get available rooms |
| `/api/hotels/{id}/amenities` | GET | Get hotel amenities |

**Hotel Data Model:**
```json
{
  "id": "hotel-001",
  "name": "Grand Istanbul Hotel",
  "location": {
    "city": "Istanbul",
    "country": "TR",
    "district": "Beyoglu",
    "coordinates": { "lat": 41.0082, "lng": 28.9784 }
  },
  "rank": 5,
  "pricePerNight": 450,
  "currency": "USD",
  "amenities": ["wifi", "pool", "spa", "restaurant", "gym"],
  "roomTypes": ["standard", "deluxe", "suite"],
  "rating": 4.7,
  "reviewCount": 1250,
  "images": ["url1", "url2"],
  "contactInfo": {
    "phone": "+90-212-xxx-xxxx",
    "email": "info@grandistanbul.com"
  }
}
```

### 2. Weather Service Integration (TNSAI OpenWeatherMapTool)
Uses TNSAI's built-in `BuiltInTool.WEATHER` to get weather information:

**Purpose:** Before making a reservation, customer can check the weather forecast for the destination city.

**TNSAI Integration:**
```java
@Action(
    type = ActionType.LLM_TOOL,
    description = "Check weather for travel destination",
    llmTool = @LLMTool(
        tools = { BuiltInTool.WEATHER },
        maxToolCalls = 1
    )
)
public String checkWeather(String city) {
    return "What is the weather like in " + city + "?";
}
```

**Use Cases:**
- Customer checks weather before booking
- Weather-based hotel recommendations (e.g., hotels with pool for sunny weather)
- Travel advisory information

### 3. Currency Exchange (TNSAI CurrencyExchangeTool)
Uses TNSAI's `BuiltInTool.CURRENCY_EXCHANGE` for price conversion:

**Purpose:** Convert hotel prices to customer's preferred currency.

**Use Cases:**
- Display prices in customer's local currency (TRY, EUR, GBP, etc.)
- Compare hotel prices across different currencies

## Enhanced Scenario Flow

### Pre-Reservation Phase
1. **Customer specifies search criteria** (location, dates, rank, budget)
2. **Weather Check** (optional but recommended):
   - Customer Agent uses Weather Tool to check destination weather
   - Display forecast for travel dates
   - May influence hotel selection (pool availability, etc.)

3. **Currency Conversion** (if needed):
   - Convert budget to hotel's currency
   - Display prices in both currencies

### Reservation Phase
(Original CNP flow continues here)

### Post-Reservation Phase
1. Reservation confirmation with weather summary
2. Travel tips based on weather forecast

## Expected Output

Console-based output showing:
1. Agent initialization with their attributes
2. DF registration of Hotel Agents
3. **Weather check for destination city** (NEW)
4. **Currency conversion if needed** (NEW)
5. Customer Agent's search query
6. CFP broadcast to hotels
7. Proposal/Refusal responses from hotels
8. Decision making process
9. Final reservation confirmation or failure message
10. **Weather summary for travel dates** (NEW)
11. (Optional) Negotiation dialogue if implemented
