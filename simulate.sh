#!/bin/bash
set -e

echo "=== Building Project ==="
mvn clean install -DskipTests -pl eureka-server,gateway-service,catalog-service,queue-service,booking-service -am > build.log 2>&1
echo "Build complete."

# Load .env for Google Client secrets
if [ -f ".env" ]; then
  export $(cat .env | grep -v '#' | awk '/=/ {print $1}')
fi

echo "=== Starting Eureka Server ==="
java -jar eureka-server/target/eureka-server-1.0.0-SNAPSHOT.jar > eureka.log 2>&1 &
EUREKA_PID=$!
sleep 15 # Wait for Eureka to fully boot

echo "=== Starting Microservices ==="
java -jar gateway-service/target/gateway-service-1.0.0-SNAPSHOT.jar > gateway.log 2>&1 &
GATEWAY_PID=$!

java -jar catalog-service/target/catalog-service-1.0.0-SNAPSHOT.jar > catalog.log 2>&1 &
CATALOG_PID=$!

java -jar queue-service/target/queue-service-1.0.0-SNAPSHOT.jar > queue.log 2>&1 &
QUEUE_PID=$!

java -jar booking-service/target/booking-service-1.0.0-SNAPSHOT.jar > booking.log 2>&1 &
BOOKING_PID=$!

echo "Waiting 60 seconds for all services to register with Eureka and initialize..."
sleep 60

# We don't want the script to exit if curl fails, we want to see the error output.
set +e

echo "=== Simulating User Journey ===" > simulation_trace.md
echo "## 1. Register User" >> simulation_trace.md
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test_user@example.com", "password":"password123"}' >> simulation_trace.md 2>&1
echo -e "\n" >> simulation_trace.md

echo "## 2. Login User" >> simulation_trace.md
LOGIN_RES=$(curl -s -i -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test_user@example.com", "password":"password123"}')

ACCESS_TOKEN=$(echo "$LOGIN_RES" | grep -oP '"accessToken":"\K[^"]+')
REFRESH_COOKIE=$(echo "$LOGIN_RES" | grep -i "Set-Cookie: refreshToken=" | cut -d' ' -f2 | tr -d '\r')

echo "**Access Token:** $ACCESS_TOKEN" >> simulation_trace.md
echo "**Refresh Cookie:** $REFRESH_COOKIE" >> simulation_trace.md
echo -e "\n" >> simulation_trace.md

echo "## 3. Search Events" >> simulation_trace.md
curl -s -X GET http://localhost:8080/api/v1/events/search?query= \
  -H "Authorization: Bearer $ACCESS_TOKEN" >> simulation_trace.md 2>&1
echo -e "\n" >> simulation_trace.md

echo "## 4. Get Event Details (ID: 1001)" >> simulation_trace.md
curl -s -X GET http://localhost:8080/api/v1/events/1001 \
  -H "Authorization: Bearer $ACCESS_TOKEN" >> simulation_trace.md 2>&1
echo -e "\n" >> simulation_trace.md

echo "## 5. Attempt Seatmap without Queue Pass (Should Fail)" >> simulation_trace.md
curl -s -X GET http://localhost:8080/api/v1/events/1001/seatmap \
  -H "Authorization: Bearer $ACCESS_TOKEN" >> simulation_trace.md 2>&1
echo -e "\n" >> simulation_trace.md

echo "## 6. Refresh Token Test" >> simulation_trace.md
curl -s -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Cookie: refreshToken=${REFRESH_COOKIE}" >> simulation_trace.md 2>&1
echo -e "\n" >> simulation_trace.md

echo "=== Cleaning up processes ==="
kill $GATEWAY_PID $CATALOG_PID $QUEUE_PID $BOOKING_PID $EUREKA_PID || true

echo "Simulation complete!"
