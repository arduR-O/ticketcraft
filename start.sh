#!/bin/bash
java -jar eureka-server/target/eureka-server-1.0.0-SNAPSHOT.jar > eureka.log 2>&1 &
sleep 10
java -jar gateway-service/target/gateway-service-1.0.0-SNAPSHOT.jar > gateway.log 2>&1 &
java -jar catalog-service/target/catalog-service-1.0.0-SNAPSHOT.jar > catalog.log 2>&1 &
java -jar queue-service/target/queue-service-1.0.0-SNAPSHOT.jar > queue.log 2>&1 &
java -jar booking-service/target/booking-service-1.0.0-SNAPSHOT.jar > booking.log 2>&1 &
sleep 30
echo "Services started."
