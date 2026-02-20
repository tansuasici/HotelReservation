import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

const BACKEND = process.env.BACKEND_URL || "http://localhost:3001";

async function fetchJson(label: string, url: string) {
  try {
    const res = await fetch(url);
    if (!res.ok) {
      console.log("[DATA]   ✗ %s → HTTP %d", label, res.status);
      return null;
    }
    const data = await res.json();
    const summary = Array.isArray(data)
      ? `[${data.length} items]`
      : JSON.stringify(data).slice(0, 200);
    console.log("[DATA]   ← %s → %s", label, summary);
    return data;
  } catch (e) {
    console.log("[DATA]   ✗ %s → %s", label, (e as Error).message);
    return null;
  }
}

export async function GET() {
  console.log("[DATA] → fetching 4 endpoints in parallel");

  const [scopNetwork, hotels, customers, activity] = await Promise.all([
    fetchJson("network", `${BACKEND}/api/scop/playground/environment/network`),
    fetchJson("hotels", `${BACKEND}/api/data/hotels`),
    fetchJson("customers", `${BACKEND}/api/customers/status`),
    fetchJson("activity", `${BACKEND}/api/activity?since=0`),
  ]);

  // SCOP returns array of NetworkEnvironmentDTOs — pick HotelEnv
  const topology = Array.isArray(scopNetwork)
    ? scopNetwork.find((env: any) => env.name === "HotelEnv") ?? scopNetwork[0]
    : scopNetwork;

  if (!topology) {
    console.log("[DATA] ✗ topology null — returning 404");
    return NextResponse.json(
      { error: "No simulation data found. Is the backend running?" },
      { status: 404 }
    );
  }

  // Enrich topology nodes with business data from hotels/customers
  // SCOP DTO only has: name, type, colorCode, degree, neighbors
  // We need: displayName, location, rank, basePrice, totalRooms, etc.
  if (topology.nodes) {
    const hotelMap = new Map<string, any>();
    if (hotels) {
      for (const h of hotels as any[]) {
        hotelMap.set(h.id, h);
      }
    }
    const customerMap = new Map<string, any>();
    if (customers) {
      for (const c of customers as any[]) {
        customerMap.set(c.customerId, c);
      }
    }

    topology.nodes = topology.nodes.map((node: any) => {
      if (node.type === "HotelAgent") {
        // Agent name is "Hotel-h001", hotel id is "h001"
        const hotelId = node.name.replace("Hotel-", "");
        const hotel = hotelMap.get(hotelId);
        if (hotel) {
          return {
            ...node,
            displayName: hotel.name,
            hotelId: hotel.id,
            location: hotel.location?.city ?? hotel.city ?? "",
            rank: hotel.rank,
            basePrice: hotel.pricePerNight,
            totalRooms: hotel.totalRooms,
          };
        }
      }
      if (node.type === "CustomerAgent") {
        const customer = customerMap.get(node.name);
        if (customer) {
          return {
            ...node,
            displayName: node.name,
            location: customer.desiredLocation,
            desiredRank: customer.desiredRank,
            maxPrice: customer.maxPrice,
          };
        }
      }
      return node;
    });
  }

  console.log(
    "[DATA] ✓ aggregate → nodes: %d | edges: %d | hotels: %d | customers: %d | activity: %d",
    topology.nodes?.length ?? 0,
    topology.edges?.length ?? 0,
    hotels?.length ?? 0,
    customers?.length ?? 0,
    activity?.length ?? 0
  );

  return NextResponse.json({ topology, hotels, customers, activity });
}
