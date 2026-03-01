# Hotel Reservation Multi-Agent System

A case study implementation of the **Hybrid Role-Based Reference Architecture for LLM-Enhanced Multi-Agent Systems**, as described in the accompanying EMAS 2026 paper. This project demonstrates how the **Contract Net Protocol (CNP)** combines with hybrid action types to create a robust, LLM-enhanced MAS using the **SCOP Framework** and **TnsAI** annotation-based meta-model.

## Reference Architecture Overview

The system implements the hybrid role-based reference architecture where roles are first-class runtime entities with four distinct action implementation types:

| Action Type | Select when... | Example in this project |
|-------------|---------------|------------------------|
| **LOCAL CODE** | Deterministic, safety-critical, or performance-sensitive operations | `canFulfillRequest()`, inventory updates, budget validation |
| **WEB SERVICE** | Well-defined HTTP API calls with stable contracts | `fetchAllHotels()`, weather forecast retrieval |
| **LLM** | Natural language generation, subjective evaluation, dynamic tool selection | `decidePricingStrategy()`, `evaluateProposals()` |
| **MCP TOOL** | External tool access through standardized protocol | Calendar synchronization |

Each action is annotated with `@ActionSpec(type=...)`, making the execution strategy explicit and enabling the `ActionExecutor` to route invocations through the full pipeline: `@BeforeActionSpec` hook -> parameter validation -> typed execution -> `@AfterActionSpec` hook.

## Architecture

```
+-----------------------------------------------------------------+
|                    Next.js Frontend (webapp/)                    |
+----------------------------+------------------------------------+
                             | REST API
+----------------------------v------------------------------------+
|                       Spring Boot API                           |
|  SimulationController - TopologyController - AgentController    |
|  ActivityController - CustomerStatusController                  |
+-----------------------------+-----------------------------------+
|                       SCOP Playground                           |
|                                                                 |
|  DirectoryFacilitator    HotelAgents         CustomerAgents     |
|  (Yellow Pages)          @AgentSpec           @AgentSpec         |
|                          HotelProviderRole    CustomerRole       |
|                          @RoleSpec            @RoleSpec          |
|                          LOCAL+LLM+WEB        LOCAL+WEB+LLM     |
|                                                                 |
|  SCOPBridge (TnsAI integration)                                 |
|  ActionExecutor -> typed executors                              |
|  NetworkEnvironment (JGraphT)                                   |
+-----------------------------------------------------------------+
```

## Annotation-Driven Design

### Agent Declaration (`@AgentSpec`)

Agents are declared with identity and optional LLM configuration:

```java
@AgentSpec(
    description = "Hotel service provider agent that handles room reservations",
    llm = @LLMSpec(
        provider = Provider.OLLAMA,
        model = "minimax-m2.1:cloud",
        temperature = 0.5f))
public class HotelAgent extends Agent { ... }
```

### Role Declaration (`@RoleSpec`)

Roles group actions into named responsibilities with communication preferences:

```java
@RoleSpec(
    description = "Hotel service provider that responds to reservation requests",
    responsibilities = {
        @Responsibility(
            name = "Negotiation",
            description = "Handle price negotiations",
            actions = {"handleNegotiateStartMessage",
                       "handleCounterOfferMessage", "handleNegotiateAcceptMessage"})
    },
    communication = @Communication(
        style = @Communication.Style(
            tone = Communication.Tone.PROFESSIONAL,
            verbosity = Communication.Verbosity.CONCISE),
        languages = {"tr", "en"}))
public class HotelProviderRole extends Role { ... }
```

### Typed Actions (`@ActionSpec`)

```java
// LOCAL CODE: deterministic business logic
@ActionSpec(type = ActionType.LOCAL,
            description = "Process incoming CFP and decide whether to make a proposal")
public void handleCFPMessage(Message<RoomQuery> msg) { ... }

// WEB SERVICE: external HTTP calls
@ActionSpec(type = ActionType.WEB_SERVICE,
    description = "Fetch all hotels from Hotel Data API",
    webService = @WebService(
        endpoint = "http://localhost:3001/api/hotels",
        method = HttpMethod.GET, timeout = 5000))
public List<Hotel> fetchAllHotels() { ... }
```

### Lifecycle Hooks

```java
// Before: dynamic pricing based on occupancy
@BeforeActionSpec("sendProposal")
private ActionParams beforeSendProposal(ActionParams p) {
    double occupancyRate = computeOccupancy();
    p.set("dynamicPrice", basePrice * multiplier);
    return p;
}

// After: market analytics accumulation
@AfterActionSpec("handleProposalMessage")
private void afterHandleProposal(ActionParams p) {
    totalProposalCount++;
    proposalPriceSum += p.getDouble("proposalPrice");
}
```

### Security-by-Construction

Sensitive data is injected via `@BeforeActionSpec` hooks, ensuring credentials never appear in LLM-visible contexts:

```java
@BeforeActionSpec("callExternalAPI")
private ActionParams injectCredentials(ActionParams p) {
    p.set("apiKey", System.getenv("API_SECRET_KEY"));
    return p;
}
```

## Contract Net Protocol Flow

```
Customer                              Hotel Agents
   |                                       |
   |---- CFP (RoomQuery) --------------->  |  broadcast to matching hotels
   |                                       |
   |<--- Proposal (RoomProposal) --------  |  or Refuse
   |                                       |
   |  [evaluate & shortlist top N]         |
   |                                       |
   |---- NegotiateStart ---------------->  |  sequential: best candidate first
   |<--- CounterOffer -------------------  |
   |---- CounterOffer ------------------>  |  ... rounds ...
   |<--- NegotiateAccept ----------------  |
   |                                       |
   |---- Accept (ReservationRequest) --->  |
   |<--- Confirm (ReservationConfirm.) --  |
```

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- Node.js 18+ & pnpm (for frontend)

### Backend

```bash
# 1. Setup environment
cp .env.example .env
# Edit .env if needed (defaults work out of the box)

# 2. Build & run
mvn clean compile
mvn spring-boot:run
```

### Frontend

```bash
cd webapp
pnpm install
pnpm dev
```

## REST API

### Simulation Control

```
POST /api/simulation?action=setup     Initialize playground & agents
POST /api/simulation?action=run       Start simulation
POST /api/simulation?action=pause     Pause
POST /api/simulation?action=stop      Stop
GET  /api/simulation/status           Current state, tick, agent count
```

### Data

```
GET  /api/data/hotels                 All hotels
GET  /api/data/hotels/{id}            Hotel by ID
GET  /api/data/hotels/search?city=Istanbul&minRank=4&maxPrice=500
GET  /api/data/hotels/cities          Available cities
GET  /api/customers/status            All customer states
GET  /api/customer/{id}/status        Single customer detail
```

### Network & Activity

```
GET  /api/network/topology            JGraphT graph (nodes + edges)
GET  /api/activity?since=0            Activity log entries
POST /api/agents/{id}/chat            Chat with an agent
```

## Configuration

All tunable parameters live in `.env` with sensible defaults:

```bash
# Server
SERVER_PORT=8080

# CNP Protocol
CNP_PROPOSAL_DEADLINE_MS=30000        # How long to wait for proposals
CNP_MAX_CANDIDATES=3                  # Shortlist size for negotiation
CNP_MAX_NEGOTIATION_ROUNDS=5          # Max rounds before forced decision

# Playground Timing
API_PORT=7070                         # Internal data-fetch port
PLAYGROUND_TIMEOUT_TICK=100000        # Simulation timeout
PLAYGROUND_STEP_DELAY=1500            # Delay between ticks (ms)

# External Services
OPENWEATHER_API_KEY=your_key_here     # openweathermap.org/api
```

## Project Structure

```
src/main/java/hotel/reservation/
+-- HotelReservationPlayground.java        Simulation entry point
+-- ActivityLog.java                       Global activity logger
+-- agent/
|   +-- CustomerAgent.java                 @AgentSpec, CNP initiator
|   +-- HotelAgent.java                    @AgentSpec, CNP participant
|   +-- DataFetcherAgent.java              @AgentSpec, API data fetcher
+-- role/
|   +-- CustomerRole.java                  @RoleSpec: LOCAL + WEB_SERVICE + LLM
|   +-- HotelProviderRole.java             @RoleSpec: LOCAL + LLM + hooks
|   +-- DataFetcherRole.java               @RoleSpec: WEB_SERVICE
|   +-- pricing/
|       +-- BuyerPricingStrategy.java      @FunctionalInterface
|       +-- SellerPricingStrategy.java     @FunctionalInterface
|       +-- LinearPricingStrategy.java     Default linear impl
+-- df/
|   +-- DirectoryFacilitator.java          Yellow pages environment
|   +-- DFEntry.java                       Registration entry
+-- message/
|   +-- MessageTypes.java                  CFP, Proposal, Accept, etc.
|   +-- RoomQuery.java                     CFP payload
|   +-- RoomProposal.java                  Proposal payload
|   +-- NegotiationOffer.java              Negotiation payload
|   +-- ReservationRequest.java            Accept payload
|   +-- ReservationConfirmation.java       Confirm payload
+-- config/
|   +-- EnvConfig.java                     .env loader
+-- data/
|   +-- model/                             Hotel, CustomerSpec, Location
|   +-- repository/                        HotelRepository, CustomerRepository
|   +-- controller/HotelDataController.java
+-- api/
    +-- HotelReservationApplication.java   Spring Boot main
    +-- SimulationController.java          /api/simulation
    +-- TopologyController.java            /api/network
    +-- ActivityController.java            /api/activity
    +-- AgentController.java               /api/agents
    +-- CustomerStatusController.java      /api/customer
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Multi-Agent System | [SCOP Framework](https://scop-framework.netlify.app/) |
| Annotation Meta-Model | [TnsAI](https://github.com/tansuasici/TnsAI) |
| Backend API | Spring Boot 3.2.0 |
| Graph Topology | JGraphT (via SCOP NetworkEnvironment) |
| Frontend | Next.js + Tailwind CSS + shadcn/ui |
| Language | Java 21, TypeScript |

## Related Publication

> T.Z. Asici, O. Gurcan, G. Kardas, "A Hybrid Role-Based Reference Architecture for LLM-Enhanced Multi-Agent Systems," in *Proc. EMAS 2026*, LNCS, Springer, 2026.

## License

MIT
