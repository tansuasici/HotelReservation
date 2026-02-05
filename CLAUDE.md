# Hotel Reservation Multi-Agent System

## Project Overview

This is a multi-agent system for hotel room reservation built using **SCOP Framework** and **TNSAI Integration**. The system implements the **Contract Net Protocol (CNP)** for agent communication and negotiation.

**Key Features:**
- REST API-based architecture (no GUI)
- Contract Net Protocol for hotel-customer negotiations
- Directory Facilitator (DF) for agent discovery
- Weather API integration using WEB_SERVICE pattern
- Dummy Hotel Data API with 8 sample hotels

## Quick Start

```bash
# 1. Setup environment
cp .env.example .env
# Edit .env and add your OPENWEATHER_API_KEY

# 2. Build
mvn clean compile

# 3. Run (Spring Boot)
mvn spring-boot:run

# 4. Build fat JAR (optional)
mvn clean install -Pshade

# Server starts at http://localhost:8080
```

## Project Structure

```
src/main/java/hotel/reservation/
├── HotelReservationPlayground.java    # Main simulation entry point
├── agent/                              # Agent implementations
│   ├── CustomerAgent.java             # CNP initiator
│   └── HotelAgent.java                # CNP participant (fetches from API)
├── role/                               # Role implementations (behaviors)
│   ├── CustomerRole.java              # Search, evaluate, reserve
│   └── HotelProviderRole.java         # Respond to CFP, confirm
├── df/                                 # Directory Facilitator
│   ├── DirectoryFacilitator.java      # Yellow pages service
│   └── DFEntry.java                   # Registration entry
├── message/                            # CNP message payloads
│   ├── MessageTypes.java              # MSG_CFP, MSG_PROPOSAL, etc.
│   ├── RoomQuery.java                 # CFP payload
│   ├── RoomProposal.java              # Proposal payload
│   ├── ReservationRequest.java        # Accept payload
│   └── ReservationConfirmation.java   # Confirm payload
├── config/                             # Configuration
│   └── AppConfig.java                 # Loads .env, provides config values
├── data/                               # Hotel Data Layer
│   ├── model/                         # Hotel, Location, Room, etc.
│   ├── repository/HotelRepository.java
│   └── controller/HotelDataController.java
└── api/                                # REST API Layer
    ├── HotelReservationApplication.java
    ├── HotelReservationController.java
    ├── WeatherController.java
    └── dto/                           # Data Transfer Objects
```

## Key Concepts

### SCOP Framework Classes
- `Agent` - Base class for agents (CustomerAgent, HotelAgent)
- `Role` - Behavior implementation (CustomerRole, HotelProviderRole)
- `Environment` - Shared space (DirectoryFacilitator extends Environment)
- `Playground` - Simulation container
- `Message<T>` - Communication between agents
- `sendMessage(type, payload, receiver)` - Send messages via Role

### Contract Net Protocol Flow
```
Customer                    Hotel Agents
    |                           |
    |------ CFP --------------->|  (Call For Proposals)
    |                           |
    |<----- Proposal -----------|  (or Refuse)
    |                           |
    |------ Accept ------------>|  (to selected hotel)
    |------ Reject ------------>|  (to others)
    |                           |
    |<----- Confirm ------------|  (reservation confirmed)
```

### Message Types
| Type | Sender | Receiver | Payload |
|------|--------|----------|---------|
| CFP | Customer | Hotels | RoomQuery |
| Proposal | Hotel | Customer | RoomProposal |
| Refuse | Hotel | Customer | String (reason) |
| Accept | Customer | Hotel | ReservationRequest |
| Reject | Customer | Hotel | String |
| Confirm | Hotel | Customer | ReservationConfirmation |

## REST API Endpoints

### Simulation Control
```bash
POST /api/simulation?action=setup    # Initialize simulation
POST /api/simulation?action=run      # Start running
POST /api/simulation?action=pause    # Pause
POST /api/simulation?action=stop     # Stop
GET  /api/simulation/status          # Get status
```

### Hotel Data API (Dummy)
```bash
GET  /api/data/hotels                # List all hotels
GET  /api/data/hotels/{id}           # Get hotel by ID
GET  /api/data/hotels/search?city=Istanbul&minRank=4&maxPrice=500
GET  /api/data/hotels/cities         # Available cities
```

### Weather API (WEB_SERVICE pattern)
```bash
GET  /api/weather/{city}             # Current weather
GET  /api/weather/forecast/{city}?days=5
```

### Booking Flow
```bash
POST /api/search                     # Start CNP search
     Body: {"location":"Istanbul","minRank":4,"maxPrice":500}
GET  /api/proposals?customerId=Customer-1
GET  /api/customer/{id}/status
GET  /api/df/entries                 # DF registrations
```

## Configuration

### .env File Setup
Copy `.env.example` to `.env` and configure:

```bash
cp .env.example .env
# Edit .env with your API keys
```

**Required:**
```bash
OPENWEATHER_API_KEY=your_key_here    # Get from openweathermap.org/api
```

**Optional:**
```bash
HOTEL_API_BASE=http://localhost:8080/api/data/hotels  # Default
WEATHER_API_BASE=https://api.openweathermap.org/data/2.5/weather
SERVER_PORT=8080
LOG_LEVEL=DEBUG
```

### config.json
```json
{
  "run": {
    "PLAYGROUND_NAME": "hotel.reservation.HotelReservationPlayground",
    "TIMEOUT_TICK": "200"
  },
  "cnp": {
    "PROPOSAL_DEADLINE_TICKS": "30"
  }
}
```

## Important Implementation Notes

1. **Message Sending**: Use `sendMessage(type, payload, receiver)` from Role class
2. **Agent Discovery**: Use `getAgent(Class, name)` from Playground
3. **DF Registration**: Hotels register with DF in their `setup()` method
4. **State Management**: CustomerRole tracks state (IDLE → SEARCHING → WAITING_PROPOSALS → EVALUATING → RESERVING → COMPLETED)
5. **Proposal Selection**: Lowest price wins, FCFS for ties (timestamp-based)

## Dependencies

- **SCOP Core**: `ai.scop:scop-core:2026.02.03`
- **SCOP UI**: `ai.scop:scop-ui:2026.02.03`
- **Spring Boot**: `3.2.0`
- **Java**: 21

## Sample Hotels (hotel-data.json)

| ID | Name | City | Stars | Price |
|----|------|------|-------|-------|
| h001 | Grand Istanbul Hotel | Istanbul | 5 | $450 |
| h002 | Luxury Palace Istanbul | Istanbul | 5 | $400 |
| h003 | Budget Inn Istanbul | Istanbul | 3 | $150 |
| h004 | Sea View Resort | Izmir | 4 | $300 |
| h005 | Ankara Business Hotel | Ankara | 4 | $250 |
| h006 | Cappadocia Cave Hotel | Nevsehir | 5 | $350 |
| h007 | Antalya Beach Resort | Antalya | 5 | $500 |

## TODO / Future Work

- [ ] Negotiation mechanism (price bargaining)
- [ ] TNSAI Currency exchange integration
- [ ] Integration tests
- [ ] WebSocket for real-time updates
