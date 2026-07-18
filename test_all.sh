#!/bin/bash
export $(cat .env | grep -v '#' | awk '/=/ {print $1}')

echo "Waiting for PostgreSQL to be ready..."
until docker exec ticketcraft-postgres pg_isready -U postgres; do
  sleep 2
done

# Force creation of users table in gateway_db just in case Liquibase fails due to WebFlux
docker exec ticketcraft-postgres psql -U postgres -d gateway_db -c "
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    provider VARCHAR(50) NOT NULL DEFAULT 'LOCAL',
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);"

java -jar eureka-server/target/eureka-server-1.0.0-SNAPSHOT.jar > eureka.log 2>&1 &
EUREKA_PID=$!
sleep 15
java -jar gateway-service/target/gateway-service-1.0.0-SNAPSHOT.jar > gateway.log 2>&1 &
GATEWAY_PID=$!
java -jar catalog-service/target/catalog-service-1.0.0-SNAPSHOT.jar > catalog.log 2>&1 &
CATALOG_PID=$!
java -jar queue-service/target/queue-service-1.0.0-SNAPSHOT.jar > queue.log 2>&1 &
QUEUE_PID=$!
java -jar booking-service/target/booking-service-1.0.0-SNAPSHOT.jar > booking.log 2>&1 &
BOOKING_PID=$!
java -jar payment-service/target/payment-service-1.0.0-SNAPSHOT.jar > payment.log 2>&1 &
PAYMENT_PID=$!
echo "Waiting for services to register with Eureka..."
while true; do
  APPS=$(curl -s http://localhost:8761/eureka/apps)
  if echo "$APPS" | grep -q 'CATALOG-SERVICE' && \
     echo "$APPS" | grep -q 'BOOKING-SERVICE' && \
     echo "$APPS" | grep -q 'QUEUE-SERVICE' && \
     echo "$APPS" | grep -q 'PAYMENT-SERVICE'; then
    echo "All services registered with Eureka!"
    break
  fi
  echo "Still waiting..."
  sleep 5
done

# We need an extra 10-20 seconds for the Gateway to pull the latest registry from Eureka
echo "Waiting for Gateway to pull the latest Eureka registry and route correctly..."
for i in {1..20}; do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/events/search?query=Queen)
  if [ "$STATUS" = "200" ]; then
    echo "Gateway is routing correctly!"
    break
  fi
  echo "Still waiting for gateway... Status: $STATUS"
  sleep 3
done

echo "Waiting an extra 25s for internal services (e.g. booking-service) to sync their Eureka registries..."
sleep 25

# Dummy data is now safely injected by Liquibase automatically.

echo "## 1a. Register & Login User 1"
curl -s -i -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test_user_1@example.com", "password":"password123", "name":"User 1"}' > /dev/null
curl -s -i -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test_user_1@example.com", "password":"password123"}' > out1.txt
ACCESS_TOKEN_1=$(cat out1.txt | grep -oP '"accessToken":"\K[^"]+')
echo "User 1 Token: $ACCESS_TOKEN_1"

echo -e "\n## 1b. Register & Login User 2"
curl -s -i -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test_user_2@example.com", "password":"password123", "name":"User 2"}' > /dev/null
curl -s -i -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test_user_2@example.com", "password":"password123"}' > out2.txt
ACCESS_TOKEN_2=$(cat out2.txt | grep -oP '"accessToken":"\K[^"]+')
echo "User 2 Token: $ACCESS_TOKEN_2"

echo -e "\n## 3. Search Events (User 1)"
EVENTS_RES=$(curl -s -X GET "http://localhost:8080/api/v1/events/search?query=Queen")
EVENT_ID=$(echo $EVENTS_RES | grep -o '"id": *[0-9]*' | head -1 | awk -F':' '{print $2}' | tr -d ' ')
echo "Found Event ID: $EVENT_ID"

echo -e "\n## 4a. Enter Seatmap (User 1)"
SEATMAP_RES_1=$(curl -s -X GET "http://localhost:8080/api/v1/events/$EVENT_ID/seatmap" -H "Authorization: Bearer $ACCESS_TOKEN_1")
SEAT_1_ID=$(echo $SEATMAP_RES_1 | grep -o '"id": *[0-9]*' | head -1 | awk -F':' '{print $2}' | tr -d ' ')
SEAT_2_ID=$(echo $SEATMAP_RES_1 | grep -o '"id": *[0-9]*' | head -2 | tail -1 | awk -F':' '{print $2}' | tr -d ' ')
echo "User 1 loaded seatmap, found seats: $SEAT_1_ID, $SEAT_2_ID"

echo -e "\n## 4b. Enter Seatmap (User 2)"
SEATMAP_RES_2=$(curl -s -X GET "http://localhost:8080/api/v1/events/$EVENT_ID/seatmap" -H "Authorization: Bearer $ACCESS_TOKEN_2")
echo "User 2 loaded seatmap"

echo -e "\n## 5. Starting Two Parallel SSE Streams for Event $EVENT_ID in background"
# User 1 SSE stream
curl -s -N -X GET http://localhost:8080/api/v1/events/$EVENT_ID/seat-stream > sse_out_1.txt &
SSE_PID_1=$!
# User 2 SSE stream
curl -s -N -X GET http://localhost:8080/api/v1/events/$EVENT_ID/seat-stream > sse_out_2.txt &
SSE_PID_2=$!
sleep 2

echo -e "\n## 6. User 1 Reserves Seats"
curl -s -X POST http://localhost:8080/api/v1/bookings \
  -H "Authorization: Bearer $ACCESS_TOKEN_1" \
  -H "Content-Type: application/json" \
  -d '{"eventId":'$EVENT_ID', "seatIds":['$SEAT_1_ID','$SEAT_2_ID']}' > out_reserve.txt
cat out_reserve.txt
BOOKING_ID=$(cat out_reserve.txt | grep -oP '"bookingId":"\K[^"]+')
echo -e "\nUser 1 Booking ID: $BOOKING_ID"

sleep 2

echo -e "\n## 7. User 1 Checkouts via Payment Service"
if [ ! -z "$BOOKING_ID" ]; then
  curl -s -X POST http://localhost:8080/api/v1/bookings/$BOOKING_ID/checkout \
    -H "Authorization: Bearer $ACCESS_TOKEN_1" \
    -H "Content-Type: application/json" \
    -d '{"cardNumber":"4242111111111111"}' > out_checkout.txt
  cat out_checkout.txt
else
  echo "Booking ID was empty, skipping checkout."
fi

# Wait a moment for Kafka messages to be processed by catalog-service and pushed to SSE
sleep 5

echo -e "\n\n## 8. User 1 SSE Stream Output"
cat sse_out_1.txt
echo -e "\n\n## 9. User 2 SSE Stream Output (User 2 should see User 1's updates!)"
cat sse_out_2.txt

echo -e "\nKilling everything"
kill $GATEWAY_PID $CATALOG_PID $QUEUE_PID $BOOKING_PID $PAYMENT_PID $EUREKA_PID $SSE_PID_1 $SSE_PID_2 || true
