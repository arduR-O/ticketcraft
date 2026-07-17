import re

file_path = "/home/ardur/projects/ticketcraft/notes/implementation_plan.md"

with open(file_path, "r") as f:
    content = f.read()

# 1. Update Kafka Topics table
content = re.sub(
    r"\| `booking-created` \| booking-service \| payment-service .*?\n",
    "",
    content
)
content = re.sub(
    r"\| `payment-processed` \| payment-service \| booking-service .*?\n",
    "",
    content
)

# 2. Update User Journey Steps 8 and 9
step8_old_regex = r"8\. CHECKOUT.*?10\. MCP SERVER"
step8_new = r"""8. CHECKOUT → User enters payment card and clicks "Pay"
   (This happens AFTER step 7 — user is on CartPage with a PENDING booking)
   └─ POST /api/v1/bookings/a1b2.../checkout
      Body: { cardNumber: "4242424242424242" }
      booking-service:
        1. Validate booking exists, status=PENDING, not expired
        2. Tokenize card → paymentToken (see "Payment Tokenization" section below)
        3. Make SYNCHRONOUS REST call to payment-service: POST /api/v1/payments/process { paymentToken, amount: totalPrice }
        4. IF SUCCESS:
             → UPDATE booking SET status = CONFIRMED
             → UNLOCK Redisson locks for seats
             → Kafka PRODUCE → seat-status-changed { seatIds, status: SOLD }
             → Return 200 { status: CONFIRMED }
        5. IF FAILED:
             → UPDATE booking SET status = CANCELLED
             → Fetch seat IDs from reserved_seats BEFORE deleting
             → DELETE reserved_seats
             → UNLOCK Redisson locks
             → Kafka PRODUCE → seat-status-changed { seatIds, status: AVAILABLE }
             → Return 400 { status: CANCELLED, reason }

   React: receives 200 or 400 immediately. No SSE or polling needed.

9. CONFIRMATION → User sees booking confirmation
   React: Navigates to ConfirmationPage if 200 OK.
   ConfirmationPage shows: booking details, seat numbers, transaction ID.
   (Email notification via a future notification-service — not in current scope)

10. MCP SERVER"""

content = re.sub(step8_old_regex, lambda m: step8_new, content, flags=re.DOTALL)

# 3. Phase 4 Context
content = content.replace(
    "> - **Kafka** as a producer (publishes `seat-status-changed` and `booking-created` events)\n> - **Kafka** as a consumer (consumes `payment-processed` events from payment-service)",
    "> - **payment-service** via REST (synchronous payment processing)\n> - **Kafka** as a producer (publishes `seat-status-changed` events)"
)

# 4. Update Checkout endpoint contract
checkout_old_regex = r"#### `POST /api/bookings/\{bookingId\}/checkout` — Initiate Payment.*?#### `GET /api/bookings/\{bookingId\}`"
checkout_new = r"""#### `POST /api/bookings/{bookingId}/checkout` — Initiate Payment
```
Path Params:
  bookingId: UUID

Request Body:
{
  "cardNumber": "4242424242424242"
}

Validation:
  - Booking exists
  - Booking status == PENDING
  - Booking not expired (expiresAt > now)
  - cardNumber matches pattern ^\d{16}$

Processing Flow:
  1. Fetch booking from DB, validate status/expiry
  2. Tokenize card number in-memory:
     "4242..." → "tok_visa_4242", "4000..." → "tok_declined"
  3. Make synchronous REST call to payment-service (POST /api/v1/payments/process)
  4. If payment SUCCESS:
       - Update booking status = CONFIRMED
       - Unlock Redisson locks
       - Produce seat-status-changed (SOLD)
       - Return 200 OK
  5. If payment FAILED:
       - Update booking status = CANCELLED
       - Delete reserved_seats
       - Unlock Redisson locks
       - Produce seat-status-changed (AVAILABLE)
       - Return 400 Bad Request

Response 200 (Success):
{
  "bookingId": "a1b2c3d4-...",
  "status": "CONFIRMED",
  "message": "Payment successful"
}

Response 400 (Payment Failed):
{
  "error": "Payment Declined",
  "message": "Card was declined by the issuer"
}

Response 404:
{ "error": "Booking not found" }
```

#### `GET /api/bookings/{bookingId}`"""
content = re.sub(checkout_old_regex, lambda m: checkout_new, content, flags=re.DOTALL)

# 5. Remove SSE endpoint contract
sse_old_regex = r"#### `GET /api/bookings/\{bookingId\}/status-stream` — SSE for booking status updates.*?### Kafka Producer Contracts"
content = re.sub(sse_old_regex, "### Kafka Producer Contracts\n", content, flags=re.DOTALL)

# 6. Remove booking-created producer contract and payment-processed consumer contract
producer_old_regex = r"\*\*Topic: `booking-created`\*\*.*?### Kafka Consumer Contracts"
content = re.sub(producer_old_regex, "### Kafka Consumer Contracts\n", content, flags=re.DOTALL)

consumer_old_regex = r"### Kafka Consumer Contracts.*?### Cart Expiration Scheduler"
content = re.sub(consumer_old_regex, "### Cart Expiration Scheduler\n", content, flags=re.DOTALL)

# 7. Update booking files to create
content = content.replace("| [booking-service/src/main/java/.../kafka/BookingCreatedProducer.java](../booking-service/src/main/java/.../kafka/BookingCreatedProducer.java) | Publishes to `booking-created` |\n", "")
content = content.replace("| [booking-service/src/main/java/.../kafka/PaymentResultConsumer.java](../booking-service/src/main/java/.../kafka/PaymentResultConsumer.java) | Consumes `payment-processed` |", "| [booking-service/src/main/java/.../client/PaymentClient.java](../booking-service/src/main/java/.../client/PaymentClient.java) | RestClient for sync calls to payment-service |")

# 8. Phase 5 updates
phase5_old_regex = r"## Phase 5: Async Messaging & Payment Service(.*?)## Phase 6: Gateway Auth & Load Balancing"
phase5_new = r"""## Phase 5: Synchronous Payment Service

> [!IMPORTANT]
> **Context for the LLM**: This phase implements user journey step 8 (Checkout & Payment). The payment service is a simple **Tomcat** service that:
> - **Exposes** `POST /api/v1/payments/process` (REST endpoint)
> - Calls a mock Stripe API (in-process, no real HTTP — just card number pattern matching)
> - Has NO database (stateless processor)
> - Has NO Kafka integrations

### Payment Processing Logic

```
Input: PaymentRequest { paymentToken, amount, bookingId }

Token resolution rules:
  paymentToken == "tok_visa_4242"  → SUCCESS (Visa test card)
  paymentToken == "tok_declined"   → FAILED (Declined test card)
  paymentToken == "tok_mc_5555"    → SUCCESS (Mastercard test card)
  paymentToken starts with "tok_"  → FAILED (Unknown card type)
  all other                        → FAILED (Invalid token format)

Output: PaymentResponse { status: "SUCCESS"|"FAILED", transactionId?, failureReason? }
```

### Endpoint Contract (payment-service)

**`POST /api/v1/payments/process`**
```
Request Body:
{
  "bookingId": "a1b2c3d4-...",
  "amount": "300.00",
  "paymentToken": "tok_visa_4242"
}

Response 200 (Success):
{
  "status": "SUCCESS",
  "transactionId": "txn_tc_1721234567_a1b2",
  "failureReason": null
}

Response 400 (Failure):
{
  "status": "FAILED",
  "transactionId": null,
  "failureReason": "Card declined by issuer"
}
```

### Files to create

| File | Purpose |
|------|---------|
| [payment-service/src/main/java/.../PaymentServiceApplication.java](../payment-service/src/main/java/.../PaymentServiceApplication.java) | Main class |
| [payment-service/src/main/java/.../controller/PaymentController.java](../payment-service/src/main/java/.../controller/PaymentController.java) | REST endpoint |
| [payment-service/src/main/java/.../service/PaymentProcessingService.java](../payment-service/src/main/java/.../service/PaymentProcessingService.java) | Token resolution + mock Stripe logic |
| [payment-service/src/main/java/.../dto/PaymentRequest.java](../payment-service/src/main/java/.../dto/PaymentRequest.java) | Incoming request DTO |
| [payment-service/src/main/java/.../dto/PaymentResponse.java](../payment-service/src/main/java/.../dto/PaymentResponse.java) | Outgoing response DTO |
| [payment-service/src/main/resources/application.yml](../payment-service/src/main/resources/application.yml) | Eureka config |

### application.yml (payment-service)
```yaml
server:
  port: 8084

spring:
  application:
    name: payment-service

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true
```

---

## Phase 6: Gateway Auth & Load Balancing"""
content = re.sub(phase5_old_regex, lambda m: phase5_new, content, flags=re.DOTALL)

# 9. Update Architecture Diagram (Mermaid)
diagram_old_regex = r"KafkaProducer2\[Kafka Producer: booking-created\].*?KafkaConsumer3\[Kafka Consumer: booking-created\]"
diagram_new = r"""PaymentClient[HTTP RestClient → Payment]
        CartExpiry[Cart Expiration Scheduler]
    end
    
    subgraph Payment["payment-service (Tomcat)"]
        MockStripe[Mock Stripe Processor]
        PaymentAPI[REST /api/payments/process]"""
content = re.sub(diagram_old_regex, lambda m: diagram_new, content, flags=re.DOTALL)

diagram_old_regex2 = r"KafkaProducer3\[Kafka Producer: payment-processed\].*?KafkaProducer3 -->\|payment-processed\| KafkaConsumer2"
diagram_new2 = r""""""
content = re.sub(diagram_old_regex2, lambda m: diagram_new2, content, flags=re.DOTALL)

diagram_old_regex3 = r"KafkaProducer2 -->\|booking-created\| KafkaConsumer3"
diagram_new3 = r"PaymentClient -.->|REST POST| PaymentAPI"
content = re.sub(diagram_old_regex3, lambda m: diagram_new3, content, flags=re.DOTALL)

diagram_old_regex4 = r"payment-service \| 8084 \| — \| Kafka-only \(no REST endpoints\)"
diagram_new4 = r"payment-service | 8084 | — | REST"
content = content.replace(diagram_old_regex4, diagram_new4)

# One extra cleanup in the diagram
content = content.replace("KafkaConsumer2\n", "")
content = content.replace("KafkaConsumer3\n", "")
content = content.replace("KafkaProducer3\n", "")
content = content.replace("KafkaProducer2\n", "")
content = content.replace("KafkaProducer3 -->|payment-processed| KafkaConsumer2\n", "")

with open(file_path, "w") as f:
    f.write(content)
print("Updated notes/implementation_plan.md successfully.")
