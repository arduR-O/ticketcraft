import process from 'node:process';
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import axios from "axios";

/**
 * PRODUCTION AUTHENTICATION NOTE
 * 
 * In this localized demo, the MCP server connects directly to the downstream microservices 
 * (catalog-service on port 8081, booking-service on port 8082) to bypass the Gateway's OAuth2 
 * and Queue Pass requirements.
 * 
 * In a production system, an MCP Server acts as an automated agent (Machine-to-Machine communication).
 * To authenticate properly:
 * 1. The MCP Server would be registered in the OAuth2 Authorization Server (e.g., Keycloak) as a Confidential Client.
 * 2. It would use the "Client Credentials Grant" to exchange its Client ID and Client Secret for a JWT Access Token.
 * 3. The MCP server would route all API calls through the API Gateway (https://api.ticketcraft.com).
 * 4. It would attach the JWT token via the `Authorization: Bearer <token>` header.
 * 5. The API Gateway would be configured to recognize the "M2M" role and automatically bypass the human-oriented 
 *    Virtual Waiting Room / Queue Pass filter, routing the request directly to the microservices.
 */

const server = new McpServer({
  name: "TicketCraft MCP Server",
  version: "1.0.0"
});

const CATALOG_URL = "http://localhost:8081/api/events";
const BOOKING_URL = "http://localhost:8082/api/bookings";

// Hardcode a mock user ID for agent operations to bypass JWT requirement in the services directly
const AGENT_HEADERS = { "X-User-Id": "agent-mcp-system-001" };

// 1. search_events
server.tool("search_events", { query: z.string() }, async ({ query }) => {
  try {
    const response = await axios.get(`${CATALOG_URL}/search?query=${encodeURIComponent(query)}`);
    return { content: [{ type: "text", text: JSON.stringify(response.data, null, 2) }] };
  } catch (error: any) {
    return { content: [{ type: "text", text: `Error searching events: ${error.message}` }] };
  }
});

// 2. get_event_details
server.tool("get_event_details", { eventId: z.number() }, async ({ eventId }) => {
  try {
    const response = await axios.get(`${CATALOG_URL}/${eventId}`);
    return { content: [{ type: "text", text: JSON.stringify(response.data, null, 2) }] };
  } catch (error: any) {
    return { content: [{ type: "text", text: `Error fetching event details: ${error.message}` }] };
  }
});

// 3. get_seatmap
server.tool("get_seatmap", { eventId: z.number() }, async ({ eventId }) => {
  try {
    const response = await axios.get(`${CATALOG_URL}/${eventId}/seatmap`);
    return { content: [{ type: "text", text: JSON.stringify(response.data, null, 2) }] };
  } catch (error: any) {
    return { content: [{ type: "text", text: `Error fetching seatmap: ${error.message}` }] };
  }
});

// 4. reserve_seats
server.tool(
  "reserve_seats",
  { eventId: z.number(), seatIds: z.array(z.number()) },
  async ({ eventId, seatIds }) => {
    try {
      const response = await axios.post(
        BOOKING_URL,
        { eventId, seatIds },
        { headers: AGENT_HEADERS }
      );
      return { content: [{ type: "text", text: JSON.stringify(response.data, null, 2) }] };
    } catch (error: any) {
      if (error.response?.status === 409) {
         return { content: [{ type: "text", text: "Error: One or more seats are already reserved or sold." }] };
      }
      return { content: [{ type: "text", text: `Error reserving seats: ${error.message}` }] };
    }
  }
);

// 5. checkout
server.tool(
  "checkout",
  { bookingId: z.string(), cardNumber: z.string() },
  async ({ bookingId, cardNumber }) => {
    try {
      const response = await axios.post(
        `${BOOKING_URL}/${bookingId}/checkout`,
        { cardNumber },
        { headers: AGENT_HEADERS }
      );
      return { content: [{ type: "text", text: JSON.stringify(response.data, null, 2) }] };
    } catch (error: any) {
      return { content: [{ type: "text", text: `Error during checkout: ${error.message}` }] };
    }
  }
);

// 6. get_booking_status
server.tool("get_booking_status", { bookingId: z.string() }, async ({ bookingId }) => {
  try {
    const response = await axios.get(`${BOOKING_URL}/${bookingId}`, { headers: AGENT_HEADERS });
    return { content: [{ type: "text", text: JSON.stringify(response.data, null, 2) }] };
  } catch (error: any) {
    return { content: [{ type: "text", text: `Error fetching booking status: ${error.message}` }] };
  }
});

async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error("TicketCraft MCP Server running on stdio");
}

main().catch((error) => {
  console.error("Fatal error running MCP Server:", error);
  process.exit(1);
});
