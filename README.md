# TicketCraft

TicketCraft is a high-concurrency ticket booking engine and distributed systems simulator. The platform is engineered to simulate flash-sale ticket distributions for major events (e.g., a 130,000+ capacity stadium concert) under extreme concurrent load. 

It guarantees strict transactional consistency, zero double-booking, and sub-millisecond query latencies, all while dynamically throttling traffic through an automated virtual waiting room.

---

## 🏗️ System Architecture & Tech Stack

This platform is structured as a Java Spring Boot microservices cluster.

* **API Gateway (`gateway-service`)**: Spring Cloud Gateway. Handles edge routing, global rate-limiting (Redis Token Bucket), and JWT authentication mapping. Protects the cluster by validating Queue Passes via a custom global filter.
* **Service Discovery (`eureka-server`)**: Netflix Eureka Server for dynamic registry, client-side load balancing, and heartbeat-based instance health monitoring.
* **Lobby Queue (`queue-service`)**: Spring WebFlux & Redis ZSETs. Prevents thread pool exhaustion by routing excess requests to a virtual waiting room. Uses an atomic Lua script to manage promotions and zombie eviction.
* **Catalog API (`catalog-service`)**: PostgreSQL Full-Text Search (GIN indices) for forgiving searches. Broadcasts real-time seat availability to connected clients via Server-Sent Events (SSE) and Redis Pub/Sub.
* **Booking Engine (`booking-service`)**: Tomcat + Virtual Threads. Uses Redisson (Redis distributed locks) to handle multi-seat transactions synchronously. 
* **Payment Worker (`payment-service`)**: Consumes Kafka events asynchronously to run simulated billing checkouts without blocking HTTP threads.
* **Frontend**: Next.js 16, React, TypeScript, and Vanilla CSS with a clean, responsive UI.
* **Agentic API (MCP Server)**: A standalone Node.js Model Context Protocol (MCP) server that exposes the entire ticketing engine to AI Agents (like Claude), allowing them to autonomously book tickets on behalf of users.

---

## ⚡ Core Technical Feats (The "Cool Things")

### 1. The Virtual Queue (Atomic Lua Scheduling)
When a highly anticipated event opens, 100,000 users hit the platform simultaneously. Instead of crashing, the Gateway routes them to the `queue-service`.
* Users are placed into a Redis Sorted Set (`waitlist`), scored by entry timestamp.
* Every 2 seconds, a distributed scheduler triggers a highly-optimized, **atomic Lua script** inside Redis.
* The script purges "zombie" sessions (users who dropped connection and failed to send a heartbeat), calculates available cluster capacity, and pops the top $N$ users from the waitlist into the `active_sessions` set.
* The frontend streams its queue position via an SSE connection and automatically fast-tracks into the seatmap once promoted.

### 2. Lock Ordering to Prevent Distributed Deadlocks
When Alice requests seats `[104, 105]` and Bob requests `[105, 104]` at the exact same millisecond, acquiring distributed locks blindly leads to cyclic deadlocks.
The `booking-service` **sorts the target seat IDs numerically** prior to requesting Redisson locks. Enforcing a strict, global lock acquisition order across all threads converts potential deadlocks into predictable, serialized wait sequences.

### 3. Server-Sent Events (SSE) with Redis Pub/Sub
Kafka is used for asynchronous data persistence, but it uses Consumer Groups which load-balance messages. If we used Kafka for real-time seatmap UI updates, only 1 of our 5 `catalog-service` pods would receive the "Seat Locked" event, leaving the users connected to the other 4 pods with stale data.
Instead, we decoupled the broadcasting bus. The booking engine publishes to **Redis Pub/Sub**, ensuring all `catalog-service` instances receive the event simultaneously and immediately push the update down to their locally connected SSE browser clients.

### 4. Split Token Security (XSS & CSRF Immunity)
To secure the microservices statelessly without sacrificing the ability to revoke sessions:
* **Access Token:** Stored exclusively in JS Heap memory (Zustand). Completely blocks CSRF attacks because the browser doesn't automatically attach it, and XSS scripts cannot easily scrape it from memory if React sanitizes inputs.
* **Refresh Token:** Stored as an `HttpOnly`, `SameSite=Strict` cookie. When the Access Token expires, an Axios interceptor catches the 401, buffers incoming requests into a Promise queue, silently calls the `/refresh` endpoint (which transmits the HttpOnly cookie), and then resumes the queued requests seamlessly.

### 5. Automated AI Agents (MCP Server)
The system is built for the future of the web. The included `mcp-server` allows autonomous AI agents to interact with the backend APIs via the **Model Context Protocol**. You can ask Claude Desktop to "Book me 2 seats for Queen" and watch it autonomously search the catalog, analyze the seatmap, reserve the locks, and checkout on your behalf.

---

## 🚀 Running the Platform Locally

To experience the platform, follow these steps to boot the cluster on your local machine.

### Prerequisites
* Java 21+
* Node.js 20+
* Docker & Docker Compose
* Maven

### 1. Boot the Infrastructure
Start the data layer (PostgreSQL, Redis, Kafka).
```bash
docker compose up -d
```
*Wait ~15 seconds for Kafka and Postgres to fully initialize.*

### 2. Boot the Microservices
We provide a convenient bash script to start the Eureka Server, API Gateway, and the 4 domain microservices in the background.
```bash
./start.sh
```
*Wait ~30 seconds. You can verify they are running by navigating to the Eureka Dashboard at `http://localhost:8761`.*

### 3. Start the Frontend Application
```bash
cd frontend
npm install
npm run dev
```

### 4. Try it out!
1. Open `http://localhost:3000` in your browser.
2. Sign in using the mock Google OAuth button.
3. Search for "Queen" or "Coldplay".
4. Enter the Virtual Waiting Room (simulated).
5. Watch the real-time seatmap update as you reserve and purchase tickets!

*(Optional) Try the AI Agent:* Start the MCP Server (`cd mcp-server && npm run build && npm start`) and connect Claude Desktop to it to let the AI book tickets for you!
