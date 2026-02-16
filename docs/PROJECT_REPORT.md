# Hotel Reservation Multi-Agent System - Technical Report

> A comprehensive guide to the architecture, components, and algorithms of this project.
> Written for developers new to the codebase.

---

## 1. Project Overview

This project implements a **Hotel Reservation System** using a **Multi-Agent System (MAS)** architecture. Instead of a traditional client-server approach, autonomous software agents (customers and hotels) negotiate with each other using the **Contract Net Protocol (CNP)** to make hotel reservations.

**Key technologies:**
- **Backend:** Java 21 + SCOP Framework (agent platform) + TnsAI annotations
- **Frontend:** Next.js 15 + TypeScript + vis-network (graph visualization)
- **LLM Integration:** Ollama (local LLM) for agent chat conversations
- **REST API:** Javalin (lightweight HTTP server for hotel data)

**What makes this different from a normal booking app:**
- Hotels and customers are **autonomous agents** that make their own decisions
- Agents **negotiate prices** through back-and-forth offers (like a bazaar)
- Hotels dynamically adjust prices based on **demand pressure** (room scarcity)
- Customers use **leverage** from competing offers to get better deals
- Everything runs in a **simulation** visible through a real-time dashboard

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                      Next.js Frontend (port 3000)                │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐          │
│  │Dashboard │ │ Network  │ │ Activity │ │  Agent   │          │
│  │  Page    │ │  Graph   │ │   Feed   │ │  Chat    │          │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘          │
│       └─────────────┴────────────┴─────────────┘               │
│                          │ polling (500ms)                       │
│  ┌───────────────────────┴──────────────────────────┐           │
│  │  API Routes: /api/sim, /api/data, /api/chat      │           │
│  └───────────────────────┬──────────────────────────┘           │
└──────────────────────────┼──────────────────────────────────────┘
                           │ file I/O + HTTP
┌──────────────────────────┼──────────────────────────────────────┐
│                      Java Backend                                │
│  ┌───────────────────────┴──────────────────────────┐           │
│  │           SimulationRunner (headless)              │           │
│  │  - Manages sim lifecycle via file-based commands   │           │
│  │  - Polls .sim-command, writes .sim-state           │           │
│  └───────────────────────┬──────────────────────────┘           │
│  ┌───────────────────────┴──────────────────────────┐           │
│  │      HotelReservationPlayground (orchestrator)    │           │
│  │  ┌──────────┐  ┌──────────────────────────────┐  │           │
│  │  │  Hotel   │  │    NetworkEnvironment         │  │           │
│  │  │  Data    │  │  ┌────────┐  ┌────────────┐  │  │           │
│  │  │  Server  │  │  │ Hotel  │  │  Customer   │  │  │           │
│  │  │ (7070)   │  │  │ Agents │  │   Agents    │  │  │           │
│  │  └──────────┘  │  └───┬────┘  └──────┬─────┘  │  │           │
│  │                │      │  messages     │        │  │           │
│  │  ┌──────────┐  │      └──────┬────────┘        │  │           │
│  │  │Directory │  │             │                  │  │           │
│  │  │Facilitator│ │    ┌────────┴───────┐         │  │           │
│  │  │  (DF)    │  │    │  ActivityLog   │         │  │           │
│  │  └──────────┘  │    └────────────────┘         │  │           │
│  │                └──────────────────────────────┘  │           │
│  └──────────────────────────────────────────────────┘           │
│  Output: output-data/ (JSON files read by frontend)              │
└──────────────────────────────────────────────────────────────────┘
```

---

## 3. Core Concepts

### 3.1 Multi-Agent System (MAS)

A MAS is a system where multiple autonomous "agents" interact to achieve goals. Each agent:
- Has its own **identity** (name, type)
- Has **roles** that define its behavior
- Communicates via **messages** (not direct method calls)
- Makes **autonomous decisions** based on its own logic

### 3.2 Contract Net Protocol (CNP)

CNP is a standard protocol for task allocation in multi-agent systems. The flow:

```
Customer (Initiator)                    Hotel (Participant)
       │                                       │
       │──── CFP (Call For Proposals) ────────>│
       │     "I need a 4★ hotel in Istanbul"   │
       │                                       │
       │<──── PROPOSAL ───────────────────────│
       │      "Grand Istanbul, $450/night"     │
       │                                       │
       │<──── REFUSE ─────────────────────────│
       │      "No rooms available"             │
       │                                       │
       │──── ACCEPT / REJECT ─────────────────>│
       │                                       │
       │<──── CONFIRM ────────────────────────│
       │      "Reservation #CONF-123"          │
```

### 3.3 Extended CNP: Negotiation Phase

This project extends standard CNP with price negotiation:

```
Customer                                Hotel
   │                                       │
   │──── NEGOTIATE_START ─────────────────>│
   │     "I offer $360/night"              │
   │                                       │
   │<──── COUNTER_OFFER ──────────────────│
   │      "How about $440/night?"          │
   │                                       │
   │──── COUNTER_OFFER ──────────────────>│
   │     "I'll go $400/night"              │
   │                                       │
   │<──── NEGOTIATE_ACCEPT ───────────────│
   │      "Deal at $400/night!"            │
   │                                       │
   │──── NEGOTIATE_ACCEPT ────────────────>│  (confirms deal)
   │                                       │
   │<──── CONFIRM ────────────────────────│
   │      "Reservation confirmed!"         │
```

### 3.4 Top-N Sequential Negotiation with Leverage

Instead of negotiating with only one hotel, customers:
1. **Shortlist** the top 3 cheapest proposals (MAX_CANDIDATES = 3)
2. **Start negotiation** with the cheapest candidate first
3. **Use leverage**: "We have a competing offer from Hotel X at $Y/night"
4. If negotiation **fails** (price too high or rejected), move to next candidate
5. If **all candidates exhausted**, the customer FAILs

---

## 4. Backend: Java Classes

### 4.1 Orchestration Layer

#### `HotelReservationPlayground` — Main simulation orchestrator

**File:** `src/main/java/hotel/reservation/HotelReservationPlayground.java`
**Extends:** `Playground` (SCOP framework base class for simulation environments)

| Method | Purpose |
|--------|---------|
| `setup()` | Initializes everything: starts Hotel Data API server, creates NetworkEnvironment, DirectoryFacilitator, hotel agents, and customer agents |
| `createHotelAgents()` | Fetches hotel data from REST API (port 7070), creates a `HotelAgent` for each hotel |
| `createCustomerAgents()` | Creates 15 `CustomerAgent` instances with predefined search criteria (city, min stars, max price) |
| `triggerAllSearches()` | Starts the CNP process for all customers |
| `triggerSearch(name)` | Starts search for a specific customer |
| `addHotelOffline(...)` | Dynamically adds a hotel agent at runtime |
| `addCustomer(...)` | Dynamically adds a customer agent at runtime |
| `writeOutputFiles()` | Serializes current state to JSON files in `output-data/` for the frontend |
| `writeTopology(...)` | Writes `topology.json` — network graph nodes and edges |
| `writeHotels(...)` | Writes `hotels.json` — hotel data from API |
| `writeCustomers(...)` | Writes `customers.json` — customer states, negotiation progress, confirmations |
| `writeActivity(...)` | Writes `activity.json` — chronological message log |
| `writeAgents(...)` | Writes `agents.json` — agent metadata (name, type, LLM model) |
| `writePrompts(...)` | Writes `prompts/{agentId}.txt` — system prompts for LLM chat |
| `regenerateNetwork()` | Rebuilds the JGraphT network topology between agents |

#### `SimulationRunner` — Headless simulation controller

**File:** `src/main/java/hotel/reservation/cli/SimulationRunner.java`

Runs the simulation as a standalone Java process controlled via file commands:

| File | Direction | Purpose |
|------|-----------|---------|
| `output-data/.sim-command` | Frontend → Java | Commands: `run`, `pause`, `stop` |
| `output-data/.sim-state` | Java → Frontend | JSON state: `{state, message, currentTick}` |

**Lifecycle:** `NOT_INITIALIZED` → `PAUSED` (setup done) → `RUNNING` → `ENDED`

| Method | Purpose |
|--------|---------|
| `main()` | Entry point — creates playground, waits for setup, enters command loop |
| `allCustomersDone()` | Checks if every customer is COMPLETED or FAILED |
| `writeState(state, msg)` | Writes `.sim-state` JSON file |
| `readCommand()` | Reads `.sim-command` text file |

When `run` command is received, customers are started with a **300ms stagger** between each to allow the frontend to show progression.

---

### 4.2 Agent Layer

#### `HotelAgent` — Represents a hotel

**File:** `src/main/java/hotel/reservation/agent/HotelAgent.java`

Each hotel agent holds its data (name, city, rank, price) and manages room availability.

| Field | Type | Purpose |
|-------|------|---------|
| `hotelId` | String | Unique identifier (e.g., "h001") |
| `hotelName` | String | Display name (e.g., "Grand Istanbul Hotel") |
| `location` | String | City name |
| `rank` | int | Star rating (1-5) |
| `basePrice` | double | Listed price per night (USD) |
| `totalRooms` | int | Total room capacity (from JSON data, or random 1-3) |
| `availableRooms` | int | Currently available rooms (decremented on reservation) |

| Method | Purpose |
|--------|---------|
| `setup()` | Adopts `HotelProviderRole` (defines behavior) + `Conversation` role (for chat) + registers with DF |
| `registerWithDF()` | Creates a `DFEntry` and registers with `DirectoryFacilitator` so customers can find this hotel |
| `reserveRoom()` | Thread-safe room reservation — decrements `availableRooms`, returns false if no rooms left |
| `getDisplayName()` | Returns hotel name for UI display |

#### `CustomerAgent` — Represents a customer

**File:** `src/main/java/hotel/reservation/agent/CustomerAgent.java`

Each customer agent holds search criteria and a negotiation budget.

| Field | Type | Purpose |
|-------|------|---------|
| `desiredLocation` | String | Target city (e.g., "Istanbul") |
| `desiredRank` | int | Minimum star rating required |
| `maxPrice` | double | Absolute maximum budget per night |
| `desiredPrice` | double | Target price for negotiation (default: maxPrice * 0.8) |

| Method | Purpose |
|--------|---------|
| `setup()` | Adopts `CustomerRole` (defines behavior) + `Conversation` role (for chat) |
| `startSearch()` | Triggers the CNP search process by calling `CustomerRole.startSearch()` |

---

### 4.3 Role Layer (Behavior)

Roles define an agent's behavior. An agent can have multiple roles. In SCOP, roles are the primary unit of behavior.

#### `CustomerRole` — CNP Initiator behavior

**File:** `src/main/java/hotel/reservation/role/CustomerRole.java`

This is the most complex class. It implements the full customer lifecycle:

**State Machine:**
```
IDLE → SEARCHING → WAITING_PROPOSALS → EVALUATING → NEGOTIATING → RESERVING → COMPLETED
                                                                              ↘ FAILED
```

**Phase 1: Search**

| Method | Purpose |
|--------|---------|
| `startSearch()` | Resets state, clears previous data, begins search process |
| `queryDirectoryFacilitator()` | Queries the DF for hotels matching (city, minRank, maxPrice). If no matches, FAILs immediately |
| `broadcastCFP(hotels)` | Sends a `RoomQuery` CFP message to each matching hotel. **Important:** registers ALL pending responses first (race condition fix), then sends messages |

**Phase 2: Proposal Collection**

| Method | Purpose |
|--------|---------|
| `handleProposalMessage(msg)` | Stores hotel's `RoomProposal`, removes from pending set, checks if all responded |
| `handleRefuseMessage(msg)` | Records refusal, removes from pending, checks deadline |
| `checkProposalDeadline()` | If all hotels responded OR 30-second deadline passed, triggers evaluation |

**Phase 3: Evaluation (Top-N Shortlisting)**

| Method | Purpose |
|--------|---------|
| `evaluateProposals()` | Sorts proposals by price (ascending), takes top 3 (`MAX_CANDIDATES`). Rejects others. If best price <= desiredPrice, accepts directly. Otherwise starts negotiation with cheapest candidate |
| `rejectProposal(proposal, reason)` | Sends REJECT message to a hotel |
| `rejectRemainingCandidates(idx)` | Rejects all shortlisted candidates except the one at `idx` |

**Phase 4: Negotiation**

| Method | Purpose |
|--------|---------|
| `startNegotiationWithCandidate(idx)` | Opens negotiation with candidate at index `idx`. First offer = `desiredPrice`. Includes leverage if there's a competing candidate (next in list) |
| `handleCounterOfferMessage(msg)` | Receives hotel's counter-price. If acceptable (<= desiredPrice), accepts. If max rounds reached, accepts if within budget or tries next candidate. Otherwise makes own counter-offer with progressive strategy: `desiredPrice + (maxPrice - desiredPrice) * progress` |
| `handleNegotiateAcceptMessage(msg)` | Hotel accepted our offer — calls `acceptNegotiation()` |
| `handleNegotiateRejectMessage(msg)` | Hotel rejected entirely — accepts original price if affordable, or tries next candidate |
| `rejectAndTryNext(reason)` | Rejects current hotel, moves to next candidate in shortlist. If no more candidates, FAILs |
| `acceptNegotiation(price)` | Sets negotiated price, rejects remaining candidates, proceeds to reservation |
| `getCompetingCandidate()` | Returns the next candidate in shortlist (for leverage price) |

**Phase 5: Reservation**

| Method | Purpose |
|--------|---------|
| `makeReservation()` | Sends ACCEPT to hotel (direct accept, no negotiation was needed) |
| `makeNegotiatedReservation(price)` | Sends NEGOTIATE_ACCEPT with agreed price to hotel |
| `handleConfirmMessage(msg)` | Receives confirmation with booking number, sets state to COMPLETED |

#### `HotelProviderRole` — CNP Participant behavior

**File:** `src/main/java/hotel/reservation/role/HotelProviderRole.java`

Handles incoming requests from customers and manages pricing.

**Key Pricing Fields:**

| Field | Formula | Purpose |
|-------|---------|---------|
| `basePrice` | From JSON data | Listed price per night |
| `baseMinPrice` | `basePrice * 0.85` | Lowest possible price (15% max discount) |
| `negotiationFlexibility` | Random 0.3–0.8 | How fast hotel reduces price during negotiation |
| `responseRate` | 1.0 (100%) | Probability of responding to CFPs (was 0.95, set to 1.0 for deterministic testing) |

**Demand Pressure Formula:**
```
effectiveMinPrice = baseMinPrice + (basePrice - baseMinPrice) * occupancy²

Where:
  occupancy = (totalRooms - availableRooms) / totalRooms

Examples (hotel with basePrice=$300, baseMinPrice=$255):
  0% occupancy (all rooms free):  effectiveMin = $255 (full discount available)
  50% occupancy:                  effectiveMin = $255 + $45 * 0.25 = $266
  75% occupancy:                  effectiveMin = $255 + $45 * 0.56 = $280
  100% occupancy (1 room left):   effectiveMin = $300 (no discount at all)
```

**Methods:**

| Method | Purpose |
|--------|---------|
| `handleCFPMessage(msg)` | Receives CFP. Checks: (1) does hotel match query? (2) rooms available? (3) should respond? If all yes, sends proposal |
| `matchesQuery(query)` | Checks location, rank >= minRank, basePrice <= maxPrice, and availability |
| `sendProposal(customer)` | Creates `RoomProposal` with hotel details and sends to customer |
| `sendRefusal(customer, reason)` | Sends REFUSE message with reason |
| `handleAcceptMessage(msg)` | Customer accepted proposal — reserves room via `reserveRoom()`, sends `ReservationConfirmation` |
| `handleRejectMessage(msg)` | Customer rejected proposal — no action needed |
| `handleNegotiateStartMessage(msg)` | Customer starts negotiation. If customer's offer >= effectiveMinPrice, accepts. Otherwise sends counter-offer |
| `handleCounterOfferMessage(msg)` | Customer sends counter-offer. Same logic: accept if >= min, counter if not, final offer on last round |
| `handleNegotiateAcceptMessage(msg)` | Customer accepted negotiated price — reserves room, sends confirmation with discount info |
| `calculateHotelCounterOffer(round, maxRounds)` | Strategy: `basePrice - (basePrice - effectiveMin) * progress * flexibility`. Higher flexibility = faster price reduction |
| `getEffectiveMinPrice()` | Calculates scarcity-adjusted minimum price using occupancy² formula |

---

### 4.4 Directory Facilitator (Service Registry)

#### `DirectoryFacilitator` — Yellow pages for hotel agents

**File:** `src/main/java/hotel/reservation/df/DirectoryFacilitator.java`
**Extends:** `Environment` (SCOP framework)

Acts as a centralized registry. Hotel agents register themselves on startup, and customer agents query it to find matching hotels.

| Method | Purpose |
|--------|---------|
| `register(entry)` | Adds a hotel's `DFEntry` to the registry |
| `deregister(agentId)` | Removes a hotel from the registry |
| `search(location, minRank, maxPrice)` | Finds all hotels matching criteria, sorted by price (ascending) |
| `getAllEntries()` | Returns all registered hotels |
| `getRegisteredCount()` | Number of registered hotels |
| `getAvailableLocations()` | Set of all cities with registered hotels |

#### `DFEntry` — Registration record

**File:** `src/main/java/hotel/reservation/df/DFEntry.java`

| Field | Purpose |
|-------|---------|
| `agentId` | Agent's unique ID in the SCOP platform |
| `agentName` | Agent's display name |
| `hotelId` | Hotel identifier (e.g., "h001") |
| `hotelName` | Hotel display name |
| `location` | City |
| `rank` | Star rating |
| `basePrice` | Listed price |
| `available` | Whether hotel is accepting reservations |

| Method | Purpose |
|--------|---------|
| `matches(location, minRank, maxPrice)` | Returns true if this hotel matches all given criteria |

---

### 4.5 Message Types

**File:** `src/main/java/hotel/reservation/message/MessageTypes.java`

Constants for message routing in the SCOP messaging system:

| Constant | Value | Direction | Payload Type |
|----------|-------|-----------|-------------|
| `MSG_CFP` | "CFP" | Customer → Hotel | `RoomQuery` |
| `MSG_PROPOSAL` | "Proposal" | Hotel → Customer | `RoomProposal` |
| `MSG_REFUSE` | "Refuse" | Hotel → Customer | `String` (reason) |
| `MSG_ACCEPT` | "Accept" | Customer → Hotel | `ReservationRequest` |
| `MSG_REJECT` | "Reject" | Customer → Hotel | `String` (reason) |
| `MSG_CONFIRM` | "Confirm" | Hotel → Customer | `ReservationConfirmation` |
| `MSG_NEGOTIATE_START` | "NegotiateStart" | Customer → Hotel | `NegotiationOffer` |
| `MSG_COUNTER_OFFER` | "CounterOffer" | Both directions | `NegotiationOffer` |
| `MSG_NEGOTIATE_ACCEPT` | "NegotiateAccept" | Both directions | `NegotiationOffer` or `ReservationRequest` |
| `MSG_NEGOTIATE_REJECT` | "NegotiateReject" | Hotel → Customer | `String` (reason) |

#### Payload Classes

**`RoomQuery`** — Customer's search request
- `customerId`, `location`, `minRank`, `maxPrice`

**`RoomProposal`** — Hotel's offer
- `proposalId`, `hotelId`, `hotelName`, `location`, `rank`, `pricePerNight`, `roomType`, `amenities`, `rating`
- `negotiatedPrice`, `isNegotiated` — Set after negotiation

**`NegotiationOffer`** — Back-and-forth negotiation message
- `proposalId`, `hotelId`, `hotelName`, `offeredPrice`, `originalPrice`
- `round`, `maxRounds`, `message` (human-readable text)

**`ReservationRequest`** — Customer's booking request
- `requestId`, `customerId`, `proposalId`, `hotelId`, `customerName`
- `numberOfNights`, `negotiatedPrice`

**`ReservationConfirmation`** — Hotel's booking confirmation
- `confirmationNumber`, `customerId`, `hotelId`, `hotelName`
- `pricePerNight`, `originalPrice`, `discountPercent`, `totalPrice`
- `roomType`, `checkInDate`, `checkOutDate`, `status`

---

### 4.6 Data Layer

#### `Hotel` — Hotel data model

**File:** `src/main/java/hotel/reservation/data/model/Hotel.java`

POJO with Jackson annotations for JSON serialization. Fields: `id`, `name`, `location`, `rank`, `pricePerNight`, `currency`, `amenities`, `roomTypes`, `rating`, `reviewCount`, `images`, `available`, `totalRooms`.

#### `HotelRepository` — In-memory hotel data store

**File:** `src/main/java/hotel/reservation/data/repository/HotelRepository.java`

Loads hotels from `hotel-data.json` (classpath resource) on initialization.

| Method | Purpose |
|--------|---------|
| `initialize()` | Reads `/hotel-data.json` from classpath, deserializes, stores in HashMap |
| `findAll()` | Returns all hotels |
| `findById(id)` | Find by hotel ID |
| `findByCity(city)` | Filter by city |
| `search(city, minRank, maxPrice)` | Multi-criteria search, sorted by price |

#### `hotel-data.json` — Hotel dataset

**File:** `src/main/resources/hotel-data.json`

Contains 11 hotels across 6 Turkish cities with deterministic room counts:

| City | Hotels | Total Rooms |
|------|--------|-------------|
| Istanbul | Grand (5★ $450, 2r), Luxury Palace (5★ $400, 2r), Budget Inn (3★ $150, 1r), Comfort (4★ $280, 2r) | 7 |
| Ankara | Business (4★ $250, 2r), Plaza (3★ $180, 1r) | 3 |
| Antalya | Beach Resort (5★ $500, 2r), Garden (3★ $200, 1r) | 3 |
| Izmir | Sea View Resort (4★ $300, 1r) | 1 |
| Nevsehir | Cappadocia Cave (5★ $350, 1r) | 1 |
| Mugla | Bodrum Boutique (4★ $280, 1r) | 1 |

---

### 4.7 API Layer

#### `HotelDataServer` — REST API for hotel data

**File:** `src/main/java/hotel/reservation/api/HotelDataServer.java`

Lightweight Javalin HTTP server on port **7070**.

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/hotels` | GET | List all hotels |
| `/api/hotels/{id}` | GET | Get hotel by ID |
| `/api/hotels/search?city=X&minRank=Y&maxPrice=Z` | GET | Filtered search |
| `/api/cities` | GET | List available cities |

#### `HotelApiClient` — HTTP client with TnsAI annotations

**File:** `src/main/java/hotel/reservation/api/HotelApiClient.java`

Demonstrates the `@Action(type = ActionType.WEB_SERVICE)` + `@WebService` annotation pattern. Uses JDK `HttpClient` (zero dependency).

| Method | Purpose |
|--------|---------|
| `fetchAllHotels()` | GET `/api/hotels` → List of all hotels |
| `searchHotels(city, minRank, maxPrice)` | GET `/api/hotels/search` with query params |
| `fetchHotelById(id)` | GET `/api/hotels/{id}` → Single hotel |

---

### 4.8 Activity Log

#### `ActivityLog` — Shared message log

**File:** `src/main/java/hotel/reservation/ActivityLog.java`

Thread-safe, in-memory log of all agent-to-agent interactions. Each entry is a record:

```java
record Entry(long timestamp, String from, String to, String type, String detail)
```

Types logged: `CFP`, `PROPOSAL`, `REFUSE`, `ACCEPT`, `REJECT`, `CONFIRM`, `NEGOTIATE`, `COUNTER_OFFER`, `NEGOTIATE_ACCEPT`, `NEGOTIATE_REJECT`, `SHORTLIST`, `FAIL`, `DEMAND_PRESSURE`, `EVALUATE`, `FALLBACK`

---

## 5. Frontend Architecture

### 5.1 Dashboard Page

**File:** `frontend/src/app/page.tsx` + `frontend/src/components/dashboard.tsx`

The main page assembles all panels. Uses `useSimulation()` hook for data polling.

### 5.2 Core Components

| Component | File | Purpose |
|-----------|------|---------|
| **SimControls** | `sim-controls.tsx` | Setup / Run / Stop buttons. Sends POST to `/api/sim` with action |
| **NetworkGraph** | `network-graph.tsx` | vis-network graph visualization. Hotel nodes colored by city, customer nodes colored by state. Animated edges on message events |
| **CustomerPanel** | `customer-panel.tsx` | Table of all customers: state, negotiation round, hotel, price, confirmation |
| **HotelPanel** | `hotel-panel.tsx` | Table of all hotels: name, city, rank, price, available/total rooms |
| **ActivityFeed** | `activity-feed.tsx` | Chronological log of all agent messages with color-coded type badges |
| **AgentChat** | `agent-chat.tsx` | Chat sidebar — select an agent, chat with its LLM persona. Supports `@agent` mention for switching. SSE streaming responses |
| **StatusPanel** | `status-panel.tsx` | Summary stats: total hotels, customers, completed, failed, messages |
| **Navbar** | `navbar.tsx` | Top bar with dark/light theme toggle |
| **MarkdownMessage** | `markdown-message.tsx` | Renders LLM responses as markdown |

### 5.3 API Routes

| Route | Method | Purpose |
|-------|--------|---------|
| `/api/sim` | GET | Read simulation state from `.sim-state` file |
| `/api/sim` | POST | Send command (setup/run/stop). On `setup`: spawns Java process |
| `/api/data` | GET | Read all JSON files from `output-data/` (topology, hotels, customers, activity, agents) |
| `/api/chat` | GET | Get chat history + system prompt for an agent |
| `/api/chat` | POST | Send message to agent, returns SSE stream of tokens |
| `/api/chat` | DELETE | Clear chat history |
| `/api/logs/[agent]` | GET | Read agent-specific log file |

### 5.4 Chat Architecture (SSE Streaming)

```
Frontend                    /api/chat (POST)                 Ollama (11434)
   │                             │                                │
   │── POST {agentId, msg} ────>│                                │
   │                             │── POST /api/chat ────────────>│
   │                             │   (stream: true)               │
   │                             │                                │
   │<── SSE: {token: "The"} ───│<── NDJSON: {"message":...} ──│
   │<── SSE: {token: " hotel"} ─│<── NDJSON: {"message":...} ──│
   │<── SSE: {token: " is"} ───│<── NDJSON: {"message":...} ──│
   │<── SSE: {done: true} ─────│                                │
   │                             │                                │
```

**Playground Mode (tool calling):**
```
Frontend → /api/chat → Ollama (with tools) → if tool_call:
  → execute tool (get_simulation_summary, get_hotels, etc.)
  → send tool result back to Ollama
  → repeat until final text response
  → stream final response word-by-word
```

### 5.5 State Colors

| Customer State | Color | Hex |
|---------------|-------|-----|
| IDLE | Gray | #71717a |
| SEARCHING | Indigo | #6366f1 |
| WAITING_PROPOSALS | Amber | #f59e0b |
| EVALUATING | Purple | #a855f7 |
| NEGOTIATING | Cyan | #06b6d4 |
| RESERVING | Amber | #f59e0b |
| COMPLETED | Green | #22c55e |
| FAILED | Red | #ef4444 |

### 5.6 Hooks

#### `useSimulation()` — Central data polling hook

**File:** `frontend/src/hooks/use-simulation.ts`

Polls `/api/sim` and `/api/data` every 500ms while simulation is running. Provides:
- `simState`: Current simulation state
- `topology`, `hotels`, `customers`, `activity`, `agents`: Parsed JSON data
- `startSetup()`, `startRun()`, `stopSim()`: Control functions

---

## 6. Full Simulation Flow (Step by Step)

### Step 1: Setup
1. User clicks **Setup** in the UI
2. Frontend POST `/api/sim` with `action: "setup"`
3. `route.ts` spawns Java process: `java -cp ... SimulationRunner`
4. `SimulationRunner.main()`:
   - Loads `config.json`
   - Creates `HotelReservationPlayground`
   - Playground `setup()`:
     - Starts `HotelDataServer` on port 7070
     - Creates `NetworkEnvironment` and `DirectoryFacilitator`
     - Fetches 11 hotels from API → creates 11 `HotelAgent` instances
     - Each `HotelAgent.setup()` → registers with DF
     - Creates 15 `CustomerAgent` instances with predefined criteria
   - Writes initial output files (topology, hotels, etc.)
   - State → `PAUSED`

### Step 2: Run
1. User clicks **Run**
2. Frontend POST `/api/sim` with `action: "run"`
3. `SimulationRunner` reads command → state → `RUNNING`
4. Spawns thread that starts each customer search with 300ms delay:

   **For each customer:**
   1. `CustomerRole.startSearch()` → state = SEARCHING
   2. `queryDirectoryFacilitator()` → DF returns matching hotels
   3. `broadcastCFP()` → sends CFP to each matching hotel
   4. State = WAITING_PROPOSALS
   5. Hotels receive CFP → `HotelProviderRole.handleCFPMessage()`:
      - Check match, rooms, availability → send PROPOSAL or REFUSE
   6. Customer receives all responses → `evaluateProposals()`:
      - Sort by price, take top 3 → SHORTLIST
      - If best price is good enough → direct ACCEPT
      - Otherwise → start negotiation with cheapest candidate
   7. **Negotiation loop** (max 5 rounds):
      - Customer offers desiredPrice (+ leverage message)
      - Hotel checks against effectiveMinPrice (demand-adjusted)
      - If acceptable → NEGOTIATE_ACCEPT
      - If not → COUNTER_OFFER with calculated price
      - Customer evaluates counter: accept if within budget, counter with progressive offer, or accept hotel's offer if our counter would exceed it
   8. On agreement → customer sends NEGOTIATE_ACCEPT → hotel reserves room → sends CONFIRM
   9. On failure → `rejectAndTryNext()` → try next candidate
   10. State = COMPLETED or FAILED

5. Every 500ms, `SimulationRunner` calls `writeOutputFiles()` → frontend polls and updates
6. When `allCustomersDone()` → final `writeOutputFiles()` → state = ENDED

### Step 3: Stop
1. User clicks **Stop** or simulation completes naturally
2. Final output written, state → `ENDED`
3. Java process exits

---

## 7. Customer Scenario Design

15 customers are designed to test specific behaviors:

| # | City | Stars | Budget | Matching Hotels | Scenario |
|---|------|-------|--------|-----------------|----------|
| C1 | Istanbul | 4★ | $460 | h001, h002, h009 (3) | **Top-3 shortlist + leverage negotiation** |
| C2 | Istanbul | 4★ | $450 | h001, h002, h009 (3) | **Competition with C1 + sequential fallback** |
| C3 | Istanbul | 3★ | $170 | h003 (1) | **Tight budget negotiation** ($170 for $150 hotel) |
| C4 | Istanbul | 3★ | $160 | h003 (1) | **Room race with C3** (1 room only) |
| C5 | Istanbul | 5★ | $380 | none | **Guaranteed FAIL** (no matching hotel) |
| C6 | Ankara | 3★ | $280 | h005, h010 (2) | **Top-2 with leverage** |
| C7 | Ankara | 3★ | $260 | h005, h010 (2) | **Competition with C6** |
| C8 | Ankara | 4★ | $270 | h005 (1) | **Single candidate, no leverage** |
| C9 | Antalya | 3★ | $530 | h007, h011 (2) | **Easy negotiation** (big budget) |
| C10 | Antalya | 5★ | $520 | h007 (1) | **Single premium candidate** |
| C11 | Izmir | 3★ | $340 | h004 (1) | **Demand pressure** (1-room hotel) |
| C12 | Izmir | 3★ | $320 | h004 (1) | **Room race with C11** → loser FAILs |
| C13 | Nevsehir | 4★ | $380 | h006 (1) | **Solo customer, moderate budget** |
| C14 | Mugla | 3★ | $300 | h008 (1) | **Room competition** |
| C15 | Mugla | 3★ | $290 | h008 (1) | **Room race with C14** → loser FAILs |

**Expected outcomes per run:**
- ~10 COMPLETED (successful bookings)
- ~5 FAILED (no match, no rooms, or exhausted candidates)
- Demand pressure visible on 1-room hotels
- Leverage visible in C1/C2 negotiations
- Sequential fallback when first candidate's rooms are taken

---

## 8. TnsAI Annotation Patterns Used

This project showcases TnsAI's annotation-based architecture:

| Annotation | Class | Purpose |
|------------|-------|---------|
| `@AgentSpec` | CustomerAgent, HotelAgent | Declares agent metadata + LLM configuration |
| `@LLMSpec` | Both agents, both roles | Specifies LLM provider, model, temperature |
| `@RoleSpec` | CustomerRole, HotelProviderRole | Declares role description + responsibilities |
| `@Responsibility` | Both roles | Groups related actions under a named responsibility |
| `@Action` | 15+ methods | Marks methods as agent actions with type (LOCAL, WEB_SERVICE) |
| `@WebService` | HotelApiClient methods | Declares HTTP endpoint, method, timeout, param style |
| `@State` | Role fields | Marks fields as observable agent state |
| `@Communication` | Both roles | Declares communication style (tone, verbosity, languages) |

---

## 9. File Structure

```
scop-hotel-reservation/
├── pom.xml                          # Maven build (Java 21, SCOP, Javalin, Jackson)
├── config.json                      # SCOP framework configuration
├── src/main/java/hotel/reservation/
│   ├── HotelReservationPlayground.java   # Main orchestrator
│   ├── ActivityLog.java                  # Shared message log
│   ├── agent/
│   │   ├── CustomerAgent.java            # Customer agent
│   │   └── HotelAgent.java              # Hotel agent
│   ├── role/
│   │   ├── CustomerRole.java             # CNP initiator behavior
│   │   └── HotelProviderRole.java        # CNP participant behavior
│   ├── df/
│   │   ├── DirectoryFacilitator.java     # Yellow pages service
│   │   └── DFEntry.java                  # Registration record
│   ├── message/
│   │   ├── MessageTypes.java             # Message type constants
│   │   ├── RoomQuery.java                # CFP payload
│   │   ├── RoomProposal.java             # Proposal payload
│   │   ├── NegotiationOffer.java         # Negotiation payload
│   │   ├── ReservationRequest.java       # Booking request
│   │   └── ReservationConfirmation.java  # Booking confirmation
│   ├── api/
│   │   ├── HotelDataServer.java          # Javalin REST server
│   │   └── HotelApiClient.java           # HTTP client (@WebService)
│   ├── data/
│   │   ├── model/
│   │   │   ├── Hotel.java                # Hotel POJO
│   │   │   └── Location.java             # Location POJO
│   │   └── repository/
│   │       └── HotelRepository.java      # In-memory store
│   └── cli/
│       └── SimulationRunner.java         # Headless runner
├── src/main/resources/
│   └── hotel-data.json                   # Hotel dataset (11 hotels)
├── output-data/                          # Runtime output (JSON files)
│   ├── .sim-state                        # Simulation state
│   ├── .sim-command                      # Frontend commands
│   ├── topology.json                     # Network graph
│   ├── hotels.json                       # Hotel data
│   ├── customers.json                    # Customer states
│   ├── activity.json                     # Message log
│   ├── agents.json                       # Agent metadata
│   └── prompts/                          # LLM system prompts
└── frontend/                             # Next.js dashboard
    └── src/
        ├── app/
        │   ├── page.tsx                  # Main page
        │   ├── layout.tsx                # Root layout
        │   └── api/
        │       ├── sim/route.ts          # Simulation control
        │       ├── data/route.ts         # Data polling
        │       ├── chat/route.ts         # Agent chat (SSE)
        │       └── logs/[agent]/route.ts # Agent logs
        ├── components/
        │   ├── dashboard.tsx             # Main dashboard
        │   ├── network-graph.tsx         # vis-network graph
        │   ├── sim-controls.tsx          # Setup/Run/Stop
        │   ├── customer-panel.tsx        # Customer table
        │   ├── hotel-panel.tsx           # Hotel table
        │   ├── activity-feed.tsx         # Message log
        │   ├── agent-chat.tsx            # Chat with agents
        │   ├── status-panel.tsx          # Summary stats
        │   ├── navbar.tsx                # Top bar
        │   └── markdown-message.tsx      # Markdown renderer
        ├── hooks/
        │   ├── use-simulation.ts         # Data polling hook
        │   └── use-theme.ts             # Theme management
        └── lib/
            ├── api.ts                    # API client functions
            ├── types.ts                  # TypeScript types + color constants
            └── utils.ts                  # Utility functions
```
