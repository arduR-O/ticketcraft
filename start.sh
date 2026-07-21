#!/bin/bash
if [ -f .env ]; then
  export $(grep -v '^#' .env | xargs)
fi
nohup java -Xms128m -Xmx256m -jar eureka-server/target/eureka-server-1.0.0-SNAPSHOT.jar > eureka.log 2>&1 &
sleep 10
nohup java -Xms128m -Xmx256m -jar gateway-service/target/gateway-service-1.0.0-SNAPSHOT.jar > gateway.log 2>&1 &
nohup java -Xms128m -Xmx256m -jar catalog-service/target/catalog-service-1.0.0-SNAPSHOT.jar > catalog.log 2>&1 &
nohup java -Xms128m -Xmx256m -jar queue-service/target/queue-service-1.0.0-SNAPSHOT.jar > queue.log 2>&1 &
nohup java -Xms128m -Xmx256m -jar booking-service/target/booking-service-1.0.0-SNAPSHOT.jar > booking.log 2>&1 &
nohup java -Xms128m -Xmx256m -jar payment-service/target/payment-service-1.0.0-SNAPSHOT.jar > payment.log 2>&1 &
sleep 30
echo "Services started."
tail -f /dev/null
