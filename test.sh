#!/bin/bash
echo "## 1. Register User"
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test3@example.com", "password":"password123"}'
echo -e "\n"

echo "## 2. Login User"
LOGIN_RES=$(curl -s -i -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test3@example.com", "password":"password123"}')

ACCESS_TOKEN=$(echo "$LOGIN_RES" | grep -oP '"accessToken":"\K[^"]+')
REFRESH_COOKIE=$(echo "$LOGIN_RES" | grep -i "Set-Cookie: refreshToken=" | cut -d' ' -f2 | tr -d '\r')

echo "$LOGIN_RES"
echo -e "\n"
echo "**Extracted Access Token:** $ACCESS_TOKEN"
echo "**Extracted Refresh Cookie:** $REFRESH_COOKIE"
echo -e "\n"

echo "## 3. Search Events"
curl -s -X GET http://localhost:8080/api/v1/events/search?query= \
  -H "Authorization: Bearer $ACCESS_TOKEN"
echo -e "\n"

echo "## 4. Get Event Details (ID: 1001)"
curl -s -X GET http://localhost:8080/api/v1/events/1001 \
  -H "Authorization: Bearer $ACCESS_TOKEN"
echo -e "\n"

echo "## 5. Attempt Seatmap without Queue Pass (Should Fail)"
curl -s -i -X GET http://localhost:8080/api/v1/events/1001/seatmap \
  -H "Authorization: Bearer $ACCESS_TOKEN"
echo -e "\n"

echo "## 6. Refresh Token Test"
curl -s -i -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Cookie: refreshToken=${REFRESH_COOKIE}"
echo -e "\n"
