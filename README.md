# Hotel Reservation Multi-Agent System

A multi-agent hotel reservation system built with the **SCOP Framework** and **TNSAI Integration**. Autonomous customer and hotel agents negotiate room prices using the **Contract Net Protocol (CNP)** with pluggable pricing strategies, a Directory Facilitator for agent discovery, and a graph-based network topology.

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                     Next.js Frontend (webapp/)               │
│                        localhost:3000                         │
└──────────────────────┬───────────────────────────────────────┘
                       │ REST API
┌──────────────────────▼───────────────────────────────────────┐
│                Spring Boot API (port 3001)                    │
│  SimulationController · TopologyController · AgentController  │
│  ActivityController · CustomerStatusController                │
├──────────────────────────────────────────────────────────────┤
│                    SCOP Playground                            │
│  ┌──────────┐  ┌──────────────┐  ┌────────────────────────┐  │
│  │    DF    │  │ HotelAgents  │  │   CustomerAgents       │  │
│  │ (Yellow  │  │ HotelProvider│  │   CustomerRole         │  │
│  │  Pages)  │  │    Role      │  │   + BuyerPricing       │  │
│  └──────────┘  │ + SellerPric.│  │     Strategy           │  │
│                └──────────────┘  └────────────────────────┘  │
│                NetworkEnvironment (JGraphT)                   │
└──────────────────────────────────────────────────────────────┘
```

**Key components:**

- **CustomerAgent** -- CNP initiator. Searches hotels via DF, collects proposals, shortlists top candidates, negotiates price sequentially, reserves.
- **HotelAgent** -- CNP participant. Responds to CFPs with proposals, handles negotiation with demand-aware pricing (room scarcity raises minimum price).
- **DirectoryFacilitator** -- Yellow pages environment. Hotels register on setup; customers query by location, rank, and price.
- **NetworkEnvironment** -- JGraphT-based graph. Customers connect to all hotels; hotels connect to same-city peers.
- **Pricing Strategies** -- `BuyerPricingStrategy` / `SellerPricingStrategy` interfaces (OCP). Default: `LinearPricingStrategy`.

## Contract Net Protocol Flow

```
Customer                              Hotel Agents
   │                                       │
   │──── CFP (RoomQuery) ────────────────►│  broadcast to all matching hotels
   │                                       │
   │◄─── Proposal (RoomProposal) ─────────│  or Refuse
   │                                       │
   │  [evaluate & shortlist top N]         │
   │                                       │
   │──── NegotiateStart ─────────────────►│  sequential: best candidate first
   │◄─── CounterOffer ────────────────────│
   │──── CounterOffer ────────────────────►│  ... rounds ...
   │◄─── NegotiateAccept ─────────────────│
   │                                       │
   │──── Accept (ReservationRequest) ────►│
   │◄─── Confirm (ReservationConfirm.) ──│
```

If negotiation fails with one candidate, the customer falls back to the next in the shortlist.

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
POST /api/simulation?action=run       Start simulation (triggers all searches)
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

See `.env.example` for the full list.

## Project Structure

```
src/main/java/hotel/reservation/
├── HotelReservationPlayground.java        Simulation entry point
├── ActivityLog.java                       Global activity logger
├── agent/
│   ├── CustomerAgent.java                 CNP initiator
│   ├── HotelAgent.java                   CNP participant
│   └── DataFetcherAgent.java             API data fetcher
├── role/
│   ├── CustomerRole.java                  Search → negotiate → reserve
│   ├── HotelProviderRole.java            Respond → counter-offer → confirm
│   ├── DataFetcherRole.java              HTTP calls to data API
│   └── pricing/
│       ├── BuyerPricingStrategy.java      @FunctionalInterface
│       ├── SellerPricingStrategy.java     @FunctionalInterface
│       └── LinearPricingStrategy.java     Default linear impl
├── df/
│   ├── DirectoryFacilitator.java          Yellow pages environment
│   └── DFEntry.java                       Registration entry
├── message/
│   ├── MessageTypes.java                  CFP, Proposal, Accept, etc.
│   ├── RoomQuery.java                     CFP payload
│   ├── RoomProposal.java                 Proposal payload
│   ├── NegotiationOffer.java             Negotiation payload
│   ├── ReservationRequest.java            Accept payload
│   └── ReservationConfirmation.java       Confirm payload
├── config/
│   └── EnvConfig.java                     .env loader (dotenv-java)
├── data/
│   ├── model/                             Hotel, CustomerSpec, Location
│   ├── repository/                        HotelRepository, CustomerRepository
│   └── controller/HotelDataController.java
└── api/
    ├── HotelReservationApplication.java   Spring Boot main
    ├── SimulationController.java          /api/simulation
    ├── TopologyController.java            /api/network
    ├── ActivityController.java            /api/activity
    ├── AgentController.java               /api/agents
    └── CustomerStatusController.java      /api/customer
```

## Sample Data

### Hotels (11 hotels across 6 cities)

| ID | Hotel | City | Stars | $/night | Rooms |
|----|-------|------|:-----:|--------:|:-----:|
| h001 | Grand Istanbul Hotel | Istanbul | 5 | 450 | 2 |
| h002 | Luxury Palace Istanbul | Istanbul | 5 | 400 | 2 |
| h003 | Budget Inn Istanbul | Istanbul | 3 | 150 | 1 |
| h004 | Sea View Resort | Izmir | 4 | 300 | 1 |
| h005 | Ankara Business Hotel | Ankara | 4 | 250 | 2 |
| h006 | Cappadocia Cave Hotel | Nevsehir | 5 | 350 | 1 |
| h007 | Antalya Beach Resort | Antalya | 5 | 500 | 2 |
| h008 | Bodrum Boutique Hotel | Mugla | 4 | 280 | 1 |
| h009 | Istanbul Comfort Hotel | Istanbul | 4 | 280 | 2 |
| h010 | Ankara Plaza Hotel | Ankara | 3 | 180 | 1 |
| h011 | Antalya Garden Hotel | Antalya | 3 | 200 | 1 |

### Customers (15 customers)

Customers target Istanbul, Ankara, Antalya, Izmir, Nevsehir, and Mugla with varying star and budget requirements. Multiple customers competing for limited rooms creates demand pressure, raising hotel minimum prices during negotiation.

## Design Principles

- **OCP (Open/Closed)** -- Pricing strategies are pluggable via `BuyerPricingStrategy` / `SellerPricingStrategy` interfaces. Add `AggressivePricingStrategy`, `AuctionPricingStrategy`, etc. without touching existing code.
- **ISP (Interface Segregation)** -- Buyer and seller strategies are separate `@FunctionalInterface`s. A class can implement one or both.
- **Constructor Injection** -- Strategies are injected through Role constructors, consistent with SCOP's pattern (`DataFetcherRole(port)`, `CustomerRole(location, rank, ...)`).
- **Environment Configuration** -- All runtime-tunable parameters in `.env` via `EnvConfig`, no hardcoded magic numbers.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Multi-Agent System | SCOP Framework 2026.02.17 |
| Agent Annotations | TNSAI Integration |
| Backend API | Spring Boot 3.2.0 |
| Graph Topology | JGraphT (via SCOP NetworkEnvironment) |
| Frontend | Next.js + Tailwind CSS + shadcn/ui |
| Configuration | dotenv-java |
| Language | Java 21, TypeScript |

## License

MIT
