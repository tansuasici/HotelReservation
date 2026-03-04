# Contract Net Protocol Dashboard

Real-time monitoring and interaction dashboard for the Hotel Reservation Multi-Agent System.

![Dashboard Overview](assets/dashboard.png)

## Features

### Simulation Control
- **Setup / Run / Pause / Stop** controls with real-time state indicators
- Configurable simulation parameters (tick delay, timeout, negotiation rounds)
- Live tick counter and agent status tracking

### Network Topology
- Interactive graph visualization of hotel and customer agents (vis-network)
- Color-coded edges showing CNP message flow (CFP, Proposal, Accept, Confirm)
- Click any node to inspect the agent's details

### Customer & Hotel Panels
- Left sidebar with live customer reservation statuses (COMPLETED / FAILED / IN_PROGRESS)
- Hotel agent listing with room availability and pricing info

### Activity Feed
- Right sidebar with chronological agent message log
- Message type badges: `CFP`, `PROPOSAL`, `LLM_EVALUATE`, `NEGOTIATE`, `CONFIRM`, etc.
- Auto-scrolls to latest activity

### Agent Chat & Inspection

![Agent Detail](assets/agent-detail.png)

- Slide-over panel for any agent: system prompt, role, responsibilities
- Full activity log filtered to the selected agent
- **Live chat** interface to converse with agents via their LLM backend

### Dark / Light Theme
- System-aware theme toggle in the navbar
- Glass-panel aesthetic with smooth transitions

## Getting Started

### Prerequisites

- **Node.js 18+**
- **pnpm** (recommended)
- Backend running on `http://localhost:8000` (see [root README](../README.md))

### Install & Run

```bash
pnpm install
pnpm dev
```

Open [http://localhost:3000](http://localhost:3000) in your browser.

### Build for Production

```bash
pnpm build
pnpm start
```

## Project Structure

```
src/
  app/
    page.tsx               Entry point (renders Dashboard)
    layout.tsx             Root layout with fonts and metadata
    api/                   Next.js API routes (proxy)
    globals.css            Tailwind base + custom tokens
  components/
    dashboard.tsx          Main layout orchestrator
    navbar.tsx             Top bar with title, status, theme toggle
    sim-controls.tsx       Setup/Run/Pause/Stop + config button
    customer-panel.tsx     Left sidebar customer list
    hotel-panel.tsx        Left sidebar hotel list
    network-graph.tsx      vis-network topology visualization
    activity-feed.tsx      Right sidebar message log
    agent-chat.tsx         Slide-over agent detail + chat
    config-panel.tsx       Simulation configuration sheet
    markdown-message.tsx   Markdown renderer for chat
    status-panel.tsx       Status indicators
    ui/                    shadcn/ui primitives (badge, button, input, sheet)
  hooks/
    use-simulation.ts      Polling + state management for simulation data
    use-theme.ts           Dark/light theme hook
  lib/
    types.ts               TypeScript interfaces (TopologyNode, Activity, etc.)
```

## API Integration

The dashboard connects to the Spring Boot backend at `http://localhost:8000`:

| Endpoint | Purpose |
|----------|---------|
| `POST /api/simulation?action=...` | Control simulation lifecycle |
| `GET /api/simulation/status` | Poll simulation state |
| `GET /api/network/topology` | Fetch agent graph (nodes + edges) |
| `GET /api/activity?since={tick}` | Incremental activity log |
| `GET /api/customers/status` | Customer reservation statuses |
| `GET /api/data/hotels` | Hotel data |
| `POST /api/agents/{id}/chat` | Chat with an agent |
