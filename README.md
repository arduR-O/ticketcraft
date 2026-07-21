# TicketCraft

TicketCraft is a high-concurrency ticket booking engine and distributed-systems simulator. It's built to model a real flash-sale ticket drop (think: a 130,000-seat stadium show going on sale) — thousands of users hammering the same handful of seats at the same instant — and to hold up: zero double-bookings, no crashed services, and a UI that stays live for everyone even as the backend sheds load under pressure.

**[🎥 Watch the demo (NOTE: Lobby size is intentionally capped at one active user to showcase the queue functionality)](https://drive.google.com/file/d/1Q4CJBvSCt6KiYLuiUXmyya4vghvfiadE/view?usp=drive_link)**

---

## 🏗️ System Architecture & Tech Stack

Six Spring Boot (3.3, Java 21) microservices behind a gateway, plus a Next.js frontend and a standalone MCP server for AI-agent access.

* **API Gateway (`gateway-service`, :8080)** — Spring Cloud Gateway (WebFlux/Netty). Terminates JWT auth, runs Google OAuth2 login, does Redis-backed IP rate limiting (token bucket), and enforces Virtual Queue passes via a custom global filter before requests reach the booking flow. Uses R2DBC (not JPA) for its user store, since Gateway is a reactive, non-blocking app.
* **Service Discovery (`eureka-server`, :8761)** — Netflix Eureka for dynamic registry and client-side load balancing between services.
* **Lobby Queue (`queue-service`, :8083)** — Spring WebFlux + Redis ZSETs. Absorbs traffic spikes in a virtual waiting room instead of letting them crash downstream services. Promotion/eviction is driven by an atomic Lua script running inside Redis.
* **Catalog API (`catalog-service`, :8081)** — Spring Web (Tomcat) + JPA. PostgreSQL full-text search (GIN index + `tsvector`) for forgiving event search, a gRPC server for internal seat-price/availability checks, and an SSE endpoint that streams live seat-status updates to the browser.
* **Booking Engine (`booking-service`, :8082)** — Tomcat + **virtual threads** (JEP 444). Owns the reservation lifecycle: acquires Redisson distributed locks, calls `catalog-service` over gRPC to validate seats, persists the cart, and drives checkout.
* **Payment Worker (`payment-service`, :8084)** — A small mock payment gateway (accepts/declines by test card token) called synchronously over REST from `booking-service`. See [Design Decisions](#-design-decisions--tradeoffs) below for why this ended up synchronous instead of Kafka-driven.
* **Frontend** — Next.js 16 (React 19, TypeScript), Zustand for client state, SWR for data fetching, Tailwind. Renders the seatmap, queue UI, search, and checkout modal.
* **Agentic API (`mcp-server`)** — A standalone Node/TypeScript **Model Context Protocol** server exposing `search_events`, `get_event_details`, `get_seatmap`, `reserve_seats`, `checkout`, and `get_booking_status` as tools, so an MCP client (e.g. Claude Desktop) can autonomously search, pick seats, and check out on a user's behalf.

Kafka, Redis, and PostgreSQL run in Docker; the six Spring Boot services and the frontend run as local processes (see [Running Locally](#-running-it-locally)).

---

## ⚡ Core Technical Features

### 1. The Virtual Queue — atomic Lua scheduling
When a hot event opens, tens of thousands of users hit the platform at once. Instead of piling into the booking flow, they're routed through `queue-service`:
* Each user lands in a Redis Sorted Set (`waitlist`), scored by arrival timestamp.
* Every 2 seconds, a scheduler runs a single **atomic Lua script** (`promote_users.lua`) inside Redis. Because Redis executes Lua scripts atomically, this is safe even with multiple `queue-service` instances running the scheduler concurrently.
* The script first evicts "zombie" sessions (heartbeat missed past the grace window) from `active_sessions`, then computes free capacity and pops that many users off the front of `waitlist` into `active_sessions`.
* The Gateway checks `active_sessions` on every request; if the caller is in it, it stamps an `X-Queue-Pass` header and lets them through — no polling required on the hot path.
* The frontend polls/streams its queue position and auto-redirects into the seatmap once promoted.

### 2. Lock ordering to prevent distributed deadlocks
If Alice requests seats `[104, 105]` and Bob requests `[105, 104]` in the same millisecond, acquiring Redisson locks in request order can deadlock both threads. `booking-service` **sorts seat IDs numerically before acquiring locks**, so every caller across every instance acquires locks in the same global order — turning a possible deadlock into a predictable, serialized wait. Locks are all-or-nothing: if any seat in the batch fails to lock, everything already acquired in that request is released immediately.

### 3. Kafka for persistence, Redis Pub/Sub for real-time fan-out
Kafka consumer groups load-balance messages across partitions — great for durable processing, bad for broadcast. If `catalog-service` runs 5 replicas and a "seat locked" event only reaches 1 of them, the other 4 keep serving stale SSE data. So the two concerns are split:
* **Kafka** (`seat-status-changed` topic) is the durable event log — `booking-service` publishes on every reservation, checkout, and expiry; `catalog-service`'s `SeatStatusConsumer` persists the resulting status to Postgres.
* **Redis Pub/Sub** is the broadcast bus — after persisting, that same consumer republishes the update to a Redis channel, which *every* `catalog-service` instance is subscribed to, so each one can push it down to its own locally-connected SSE clients. Every user sees the seat turn grey in real time, regardless of which pod they're pinned to.

### 4. Split-token auth: XSS/CSRF resistance + refresh token rotation
* **Access token** lives only in JS heap memory (a non-persisted Zustand store) — never in `localStorage` or a readable cookie, so a garden-variety XSS payload can't casually exfiltrate it, and it's never auto-attached by the browser, so it's not a CSRF vector either.
* **Refresh token** is an `HttpOnly`, `SameSite=Strict` cookie, invisible to JS entirely.
* An Axios response interceptor catches `401`s, and uses **promise queuing** so that if 3 requests 401 at once, only *one* refresh call fires — the other two await the same in-flight promise instead of racing the backend.
* The refresh endpoint is called cleanly, bypassing the app's own interceptor, so a 401 on `/auth/refresh` itself can't recurse into another refresh attempt.
* Server-side, refresh tokens use **rotation with family IDs**: every refresh mints a new token and invalidates the old one, all sharing one `family_id` in Redis. If a token that was already marked "used" gets replayed (a strong signal of theft), the gateway revokes the **entire family**, killing every session tied to that login instantly rather than just the one token.

### 5. Automated AI agents (MCP Server)
The included `mcp-server` exposes the ticketing engine over the Model Context Protocol, so an MCP-compatible client can autonomously search events, inspect the seatmap, reserve seats, and check out on a user's behalf — e.g. "Book me 2 seats for Queen" as a single natural-language instruction. It talks directly to `catalog-service` and `booking-service`, deliberately bypassing the Gateway's OAuth2/Queue-Pass path for this local demo (see [Limitations](#-known-limitations--whats-intentionally-simplified) for the production-shaped alternative).

---

## 🧠 Design Decisions & Tradeoffs

A few choices that were deliberate rather than accidental, and the reasoning behind them:

| Decision | What we did | Why |
|---|---|---|
| **Payment flow: sync vs async** | `booking-service` calls `payment-service` with a **synchronous REST call**, not an async Kafka round-trip. | Checkout is a 1:1, "wait a second for a result" interaction from the user's point of view — async messaging adds real coordination complexity (correlating responses, timeouts, idempotency) for a flow the user is actively watching. Kafka is still used, but for *fan-out* (seat status), not for this point-to-point request/response. |
| **Concurrency model per service** | `booking-service` runs Tomcat + **virtual threads**; `queue-service` runs **WebFlux + Netty**. | `booking-service` does blocking JDBC/JPA work — virtual threads unmount from the carrier thread the instant they block on I/O, so blocking code doesn't need a rewrite to scale. `queue-service` only talks to Redis via the fully non-blocking Lettuce driver, so pairing it with WebFlux keeps the whole vertical slice non-blocking and gives free, clean backpressure handling for its SSE queue-position stream. |
| **Inter-service calls: gRPC vs REST** | `booking-service` → `catalog-service` seat/price validation uses **gRPC over HTTP/2**, not REST/JSON. | Protobuf parses far cheaper than JSON reflection at scale, and HTTP/2 multiplexes many concurrent calls over one persistent connection instead of paying per-request TCP/TLS setup cost. |
| **Money as `BigDecimal`/string, not integer cents** | Prices are `DECIMAL(10,2)` in Postgres, `BigDecimal` in Java, sent over gRPC as a `string` field (with an explicit comment explaining why). | Integer-minor-units breaks down across currencies with different decimal precision (JPY has 0, KWD has 3) — added complexity this project didn't need. Passing `BigDecimal` as a string over Protobuf sidesteps IEEE-754 float rounding without giving up decimal precision. |
| **Lock lease vs cart expiry alignment** | Redisson seat locks lease for **11 minutes**; the cart/booking expires at **10 minutes**; a scheduler sweeps expired carts every **60 seconds**. | The lock always outlives the cart by more than one sweep interval, so the scheduler is guaranteed to reclaim seats *before* the lock could silently expire on its own and open a race window for a second user. |
| **Full-text search: `plainto_tsquery`, not hand-rolled `to_tsquery`** | Search uses `REPLACE(plainto_tsquery('english', :query)::text, '&', '|')::tsquery` rather than manually splitting the query string and joining terms with `|`. | An earlier version stripped punctuation and joined terms with `|` by hand, which crashed on trailing/duplicate operators (e.g. a query ending in a space produced invalid `tsquery` syntax) and needed regex sanitization. Letting `plainto_tsquery` do stemming/stop-word handling first, then safely flipping its AND (`&`) to OR (`|`) at the tsquery level, gets forgiving OR-search with zero hand-written sanitization and no injection surface. |
| **DTOs everywhere, never raw JPA entities over REST** | Every controller returns purpose-built response records (`EventResponse`, `SeatResponse`, `EventDetailResponse`, …), never `@Entity` classes directly. | Serializing lazy-loaded entities after the transaction/session has closed throws `LazyInitializationException`; it also risks silent N+1 query storms and leaking internal columns. DTOs plus explicit `JOIN FETCH` queries (e.g. in `SeatRepository`) keep reads to a single round trip. |
| **Hardcoded server-side pagination (`PAGE_SIZE = 20`)** | Page size is fixed server-side; clients can't pass an arbitrary `size`. | Letting clients choose page size is a free DoS vector (`?size=1000000` dumping the seats table into JVM heap). Zero-trust default: the server decides, not the caller. |
| **All config in YAML** | Every service uses `application.yml`, no `.properties`. | Nested Gateway route/filter *lists* are painful to express as flat properties (manual `[0]`, `[1]` indices); YAML's native list syntax and hierarchical structure keep the Gateway config readable, and it matches the format everything else (Docker Compose, CI) already uses. |
| **Local dev runs Java on the host, not in Docker** | Docker Compose only runs the *data layer* (Postgres, Redis, Kafka, Zipkin, Prometheus); the six Spring Boot services run as plain `java -jar` processes via `start.sh`. | Compiling/hot-reloading JVM services inside containers during active development is slow and memory-heavy; Eureka-based discovery means the services find each other fine on `localhost` without needing container networking. |

---

## 🔥 Load Testing

Two k6 scripts were built specifically to probe correctness and resilience under contention (see `load-test/`). Both scripts locally forge valid JWTs (signed with the shared HMAC secret) and spoof `X-Forwarded-For` per virtual user, so the test can simulate a large distributed user base without the Auth flow itself becoming the bottleneck.

### Contention test — data integrity under a hard collision
**Setup:** one setup request fetches the live seatmap and pins exactly **10** specific seat IDs. **Load:** 1,000 virtual users then fire simultaneous `POST /api/v1/bookings`, each trying to grab 2 random seats out of *only those 10*.

```
checks_total.......: 3000
✓ pass 200
✗ reserve 200/201 ↳ 0% — ✓ 4 / ✗ 996
✗ reserve 409 Conflict ↳ 94% — ✓ 948 / ✗ 52
```

Out of 1,000 users fighting over the same 10 seats, **exactly 4 bookings succeeded** (locking all 8 bookable seats out of that pool), and 948 requests were correctly rejected with `409 Conflict`. **Result: zero double-bookings.** The Redisson locks serialized access to the contested rows exactly as designed.

### Tiered flash-sale test — resilience under organic ramp
**Setup:** traffic ramps 10 → 100 → 1,000 VUs over ~2 minutes, each VU running the full funnel (join queue → fetch seatmap → reserve 2 random seats → checkout).

```
✓ pass 200
✗ seatmap 200 ↳ 91% — ✓ 3770 / ✗ 345
✗ reserve 200/201 ↳ 79% — ✓ 2610 / ✗ 686
```

All users got a queue pass (1,000 VUs was under the `active_sessions` cap), but the downstream services hit real hardware limits:
- **345 seatmap failures** — `catalog-service` runs on Tomcat's default 200-thread pool; with 1,000 concurrent callers, the pool saturated and the excess requests eventually timed out (`504`/`500`).
- **686 reserve failures** — a mix of organic `409` seat collisions and the Resilience4j **circuit breaker** in `booking-service` tripping open once its gRPC calls to the struggling `catalog-service` started timing out:

```
CircuitBreaker 'catalogGrpcCircuitBreaker' is OPEN and does not permit further calls
```

Rather than let requests pile up and hang, the breaker fast-failed to a `409` the moment latency spiked.

Despite the hardware being fully saturated, the system processed **2,610 confirmed bookings (5,220 seats)** in that window with **zero double-bookings**, and degraded via `409`s and fast-fails instead of crashing outright — the intended behavior for a system under real backpressure. HTML reports from these runs are included at the repo root (`contention-report.html`, `tiered-report.html`).

---

## 🚀 Running It Locally

### Prerequisites
* Java 21+
* Node.js 20+
* Maven
* Docker & Docker Compose
* A Google OAuth2 Client ID/Secret (for login) — set in a `.env` file at the repo root as `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`

### 1. Boot the data layer
```bash
docker compose up -d
```
This starts PostgreSQL, Redis, Kafka (KRaft mode, single node), Zipkin, and Prometheus. Give it ~15 seconds to finish initializing before the next step.

### 2. Build the services
```bash
mvn clean package -DskipTests
```

### 3. Boot the microservices
```bash
./start.sh
```
This starts `eureka-server`, `gateway-service`, `catalog-service`, `queue-service`, `booking-service`, and `payment-service` in the background, in that order (with sleeps to let Eureka come up first). Check `http://localhost:8761` to confirm all six have registered.

### 4. Start the frontend
```bash
cd frontend
npm install
npm run dev
```

### 5. Try it out
1. Open `http://localhost:3000`.
2. Sign in with Google OAuth.
3. Search for "Queen" or "Coldplay" (seed data ships with a few demo events).
4. Open the event and watch the seatmap; reserve and check out seats (test card tokens like `tok_visa_4242` succeed, `tok_declined` fails).
5. Open the same event in a second session to see the seatmap update live via SSE as seats get locked/sold.

*(Optional) Run the AI agent:* `cd mcp-server && npm install && npm run build && npm start`, then point an MCP client (e.g. Claude Desktop) at it and ask it to find and book tickets for you.

*(Optional) Run the load tests:* install [k6](https://k6.io/), then from `load-test/`, `k6 run contention-test.js` or `k6 run tiered-test.js` against a running local stack.

---

## 🧪 Testing

Unit and integration tests focus on the trickiest concurrency and correctness surfaces specifically — Redisson lock acquisition/release (`SeatLockServiceTests`), the full reservation → checkout flow (`BookingServiceTests`, `BookingIntegrationTests`), the Kafka seat-status consumer (`SeatStatusConsumerTests`), the queue promotion scheduler (`QueueSchedulerTests`), the gRPC seat service, and the Gateway's queue-pass validation filter — rather than just CRUD happy paths.

---

## 🔭 Future Scope

- **Real payment provider integration** (Stripe test mode) in place of the mock `payment-service`, including webhook-driven confirmation instead of a synchronous call.
- **Seat-level outbox pattern** for the booking → Kafka publish step, so a DB commit and the Kafka publish can't drift apart if the process crashes between them.
- **Kubernetes deployment path**: drop Eureka in favor of native K8s DNS service discovery, replace Spring Cloud Gateway with an Ingress controller (or a managed API Gateway) for edge rate-limiting/TLS termination, and move Postgres/Redis/Kafka to managed equivalents (RDS/ElastiCache/MSK).
- **M2M auth for the MCP server** — route it through the Gateway using an OAuth2 Client Credentials grant instead of hitting `catalog-service`/`booking-service` directly, so agent traffic gets the same auth, rate-limiting, and observability as human traffic.
- **Distributed tracing dashboards** — Zipkin is already wired up in Docker Compose; wiring trace propagation through the gRPC and Kafka hops end-to-end is the natural next step.
- **Horizontal scale-out test** — the load tests above ran against a single instance of each service; a natural next experiment is running 2+ replicas of `catalog-service`/`booking-service` behind Eureka's load balancer to validate the Redis Pub/Sub fan-out and lock-ordering guarantees actually hold across pods, not just threads.

## ⚠️ Known Limitations / What's Intentionally Simplified

- **Payment is mocked.** `payment-service` accepts/declines based on a hardcoded set of test tokens (`tok_visa_4242`, `tok_declined`, …) rather than integrating a real processor.
- **Single-node Kafka and Redis.** Fine for local development and load testing on one machine; production would need a proper multi-broker/multi-node cluster for durability.
- **The MCP server bypasses the Gateway.** It calls `catalog-service`/`booking-service` directly with a hardcoded agent user ID, skipping OAuth2 and the Queue Pass check — a deliberate simplification for a local demo, documented in-code alongside the production-shaped alternative (Gateway + OAuth2 Client Credentials for M2M traffic).
- **No seat-hold beyond the lock/cart-expiry window.** If a user abandons a cart, seats free up after the ~10-minute expiry sweep rather than instantly.
- **Single-region design.** There's no cross-region replication or failover story here — this project is about correctness under concurrency on one cluster, not global HA.