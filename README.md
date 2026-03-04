# Hotel Reservation Multi-Agent System

A case study implementation of the **Hybrid Role-Based Reference Architecture for LLM-Enhanced Multi-Agent Systems**, as described in the accompanying EMAS 2026 paper. This project demonstrates how the **Contract Net Protocol (CNP)** combines with hybrid action types to create a robust, LLM-enhanced MAS using the **SCOP Framework**.

## Reference Architecture Overview

The system implements the hybrid role-based reference architecture where roles are first-class runtime entities with four distinct action implementation types:

| Action Type | Select when... | Example in this project |
|-------------|---------------|------------------------|
| **LOCAL CODE** | Deterministic, safety-critical, or performance-sensitive operations | `canFulfillRequest()`, inventory updates, amenity matching |
| **WEB SERVICE** | Well-defined HTTP API calls with stable contracts | `fetchAllHotels()`, `fetchWeather()` |
| **LLM** | Natural language generation, subjective evaluation, dynamic tool selection | `decidePricingStrategy()`, `evaluateProposals()`, `selectSearchStrategy()` |
| **MCP TOOL** | External tool access through standardized protocol | `syncCalendar()` — reservation calendar sync |

Each action is annotated with `@ActionSpec(type=...)`, making the execution strategy explicit and enabling the `ActionExecutor` to route invocations through the full pipeline: `@BeforeActionSpec` hook -> parameter validation -> typed execution -> `@AfterActionSpec` hook.

## Architecture

```
+----------------------------------------------------------------+
|                   Next.js Frontend (webapp/)                   |
+---------------------------+------------------------------------+
                            | REST API
+---------------------------v------------------------------------+
|                      Spring Boot API                           |
|  SimulationController - TopologyController - AgentController   |
|  ActivityController - CustomerStatusController                 |
+----------------------------+-----------------------------------+
|                      SCOP Playground                           |
|                                                                |
|  DirectoryFacilitator   HotelAgents        CustomerAgents      |
|  (Yellow Pages)         @AgentSpec         @AgentSpec          |
|                         HotelProviderRole  CustomerRole        |
|                         @RoleSpec          @RoleSpec           |
|                         LOCAL+LLM+WEB     LOCAL+WEB+LLM        |
|                                                                |
|  SCOPBridge                                                    |
|  ActionExecutor -> typed executors                             |
|  NetworkEnvironment (JGraphT)                                  |
+----------------------------------------------------------------+
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
        endpoint = "http://localhost:8000/api/hotels",
        method = HttpMethod.GET, timeout = 5000))
public List<Hotel> fetchAllHotels() { ... }

// LLM: subjective evaluation using language model
@ActionSpec(type = ActionType.LLM,
    description = "Decide pricing strategy based on market conditions and customer profile")
public double decidePricingStrategy(RoomQuery query) { ... }
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
@BeforeActionSpec("fetchWeather")
private ActionParams beforeFetchWeather(ActionParams params) {
    String apiKey = EnvConfig.openWeatherApiKey();
    String weatherBase = EnvConfig.weatherApiBase();
    params.set("apiKey", apiKey);
    params.set("weatherBase", weatherBase);
    return params;
}
```

## Contract Net Protocol Flow

```
Customer                              Hotel Agents
   |                                       |
   |  [selectSearchStrategy — LLM]        |
   |                                       |
   |---- CFP (RoomQuery) --------------->  |  broadcast (rooms, amenities, price)
   |                                       |
   |<--- Proposal (RoomProposal) --------  |  or Refuse (amenity/rank mismatch)
   |                                       |
   |  [evaluateProposals — LLM]           |
   |                                       |
   |---- NegotiateStart ---------------->  |  sequential: best candidate first
   |<--- CounterOffer -------------------  |
   |---- CounterOffer ------------------>  |  ... rounds ...
   |<--- NegotiateAccept ----------------  |
   |                                       |
   |---- AWARD (ReservationRequest) ---->  |
   |<--- Confirm (ReservationConfirm.) --  |  [syncCalendar — MCP]
```

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- Node.js 18+ & pnpm (for frontend)

### Backend

```bash
cd api

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

## Weather Integration

Weather data influences both pricing and evaluation:

- **Hotel side**: Good weather → +5% price premium (higher demand), bad weather → -7% discount (lower demand)
- **Customer side**: Weather context is included in LLM evaluation prompt, affecting value-for-money perception
- **Graceful degradation**: No API key or API error → weather effect silently skipped
- **Caching**: Weather is fetched once per city per simulation run

Set `OPENWEATHER_API_KEY` in `api/.env` to enable.

## Configuration

All tunable parameters live in `api/.env` with sensible defaults:

```bash
# Server
SERVER_PORT=8000

# CNP Protocol
CNP_PROPOSAL_DEADLINE_MS=30000        # How long to wait for proposals
CNP_MAX_CANDIDATES=3                  # Shortlist size for negotiation
CNP_MAX_NEGOTIATION_ROUNDS=5          # Max rounds before forced decision

# Playground Timing
API_PORT=8000                         # Internal data-fetch port (same as SERVER_PORT)
PLAYGROUND_TIMEOUT_TICK=100000        # Simulation timeout
PLAYGROUND_STEP_DELAY=1500            # Delay between ticks (ms)

# LLM (optional, enables AI-driven pricing & evaluation)
OLLAMA_BASE_URL=http://localhost:11434 # Ollama server URL
LLM_TIMEOUT_MS=10000                  # Request timeout
LLM_FALLBACK_ON_ERROR=true            # Use deterministic fallback on LLM failure

# External Services
OPENWEATHER_API_KEY=your_key_here     # openweathermap.org/api
```

