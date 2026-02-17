export interface SimulationStatus {
  state: string;
  currentTick: number;
  agentCount: number;
  registeredHotels: number;
  message: string;
}

export interface TopologyNode {
  name: string;
  displayName: string;
  type: "HotelAgent" | "CustomerAgent" | "DirectoryFacilitator" | "NetworkEnvironment";
  hotelId?: string;
  location?: string;
  rank?: number;
  basePrice?: number;
  availableRooms?: number;
  totalRooms?: number;
  desiredRank?: number;
  maxPrice?: number;
  model?: string;
}

export interface TopologyEdge {
  from: string;
  to: string;
}

export interface Topology {
  nodes: TopologyNode[];
  edges: TopologyEdge[];
}

export interface Hotel {
  id?: string;
  name: string;
  hotelName?: string;
  city?: string;
  location?: { city?: string } | string;
  stars?: number;
  rank?: number;
  pricePerNight?: number;
  rooms?: { pricePerNight?: number }[];
  basePrice?: number;
  price?: number;
}

export interface SelectedProposal {
  hotelId: string;
  hotelName: string;
  pricePerNight: number;
}

export interface Confirmation {
  confirmationNumber: string;
  hotelName: string;
  totalPrice: number;
  discountPercent: number;
  pricePerNight: number;
}

export interface CustomerStatus {
  customerId: string;
  state: string;
  desiredLocation: string;
  desiredRank: number;
  maxPrice: number;
  proposalCount: number;
  selectedProposal: SelectedProposal | null;
  confirmation: Confirmation | null;
  negotiationRound: number;
  negotiatingHotel: string;
  lastOffer?: number;
  negotiationHistory?: string[];
}

export interface ActivityEntry {
  timestamp: number;
  from: string;
  to: string;
  type: string;
  detail: string;
}

export interface ChatMessage {
  role: "user" | "agent";
  content: string;
}

export type SimState =
  | "NOT_INITIALIZED"
  | "PAUSED"
  | "RUNNING"
  | "ENDED";

export const CITY_COLORS: Record<string, string> = {
  Istanbul: "#6366f1",
  Izmir: "#22c55e",
  Ankara: "#f59e0b",
  Nevsehir: "#a855f7",
  Antalya: "#06b6d4",
  Bodrum: "#ef4444",
  Bursa: "#eab308",
};

export const CUSTOMER_COLOR = "#f59e0b";
export const DEFAULT_CITY_COLOR = "#71717a";

export const MSG_COLORS: Record<string, string> = {
  CFP: "#6366f1",
  PROPOSAL: "#22c55e",
  REFUSE: "#ef4444",
  ACCEPT: "#22c55e",
  REJECT: "#ef4444",
  CONFIRM: "#34d399",
  EVALUATE: "#a855f7",
  NEGOTIATE: "#06b6d4",
  COUNTER_OFFER: "#f59e0b",
  NEGOTIATE_ACCEPT: "#22c55e",
  NEGOTIATE_REJECT: "#ef4444",
};

export const STATE_COLORS: Record<string, string> = {
  IDLE: "#71717a",
  SEARCHING: "#6366f1",
  WAITING_PROPOSALS: "#f59e0b",
  EVALUATING: "#a855f7",
  NEGOTIATING: "#06b6d4",
  RESERVING: "#f59e0b",
  COMPLETED: "#22c55e",
  FAILED: "#ef4444",
};
