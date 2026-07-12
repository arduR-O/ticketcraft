# TicketCraft

TicketCraft is a high-concurrency ticket booking engine and distributed systems simulator modeled after Ticketmaster. The project is designed to simulate flash-sale ticket distributions for major events (e.g., a 132,000 capacity stadium concert) under heavy traffic.

## Architecture & Tech Stack

This platform is structured as a Java Spring Boot microservices cluster running inside Docker containers:

* **API Gateway (`gateway-service`)**: Spring Cloud Gateway. Handles routing, rate-limiting (Redis Token Bucket), and JWT authentication propagation.
* **Service Discovery (`eureka-server`)**: Eureka Server for dynamic registry and heartbeat-based instance health monitoring.
* **Lobby Queue (`queue-service`)**: Spring WebFlux & Redis ZSET. Prevents microservice thread pool exhaustion by routing excess requests to a virtual waiting room.
* **Catalog API (`catalog-service`)**: PostgreSQL Full-Text Search (GIN indices) and gRPC Seat Checking.
* **Booking Engine (`booking-service`)**: Redisson (Redis locks) to handle multi-seat transactions synchronously, and Server-Sent Events (SSE) for real-time seat map state synchronization.
* **Payment Worker (`payment-service`)**: Consumes Kafka events asynchronously to run simulated billing checkouts.

## Core Technical Solutions

1. **Anti-Double Booking**: Utilizes Redisson distributed locking on sorted seat arrays to prevent deadlocks and enforce mutual exclusion.
2. **Flash-Sale Throttling**: Implements virtual queue promotion using Redis ZSETs, keeping downstream active sessions capped at 10,000 users.
3. **Async Load Buffering**: Offloads payment verification to an asynchronous Apache Kafka event log to prevent database connection pool exhaustion.
4. **Real-time Map Updates**: Uses non-blocking Server-Sent Events (Netty/WebFlux) to stream live seat color state changes (Reserved/Sold/Available) to all connected frontends.

## Getting Started

### Prerequisites
* Java 17
* Maven 3.8+
* Docker & Docker Compose

### Running Locally
1. Clone the repository
2. Run infrastructure containers:
   ```bash
   docker compose up -d
   ```
3. Build and package parent POM:
   ```bash
   mvn clean install
   ```
