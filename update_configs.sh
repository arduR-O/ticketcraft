#!/bin/bash
for svc in gateway-service catalog-service booking-service payment-service queue-service eureka-server; do
  file="${svc}/src/main/resources/application.yml"
  if [ -f "$file" ]; then
    echo -e "\nmanagement:\n  endpoints:\n    web:\n      exposure:\n        include: prometheus, health, info\n  metrics:\n    tags:\n      application: \${spring.application.name}" >> "$file"
  fi
done

# Specifically tune HikariCP for DB services
for svc in catalog-service booking-service payment-service; do
  file="${svc}/src/main/resources/application.yml"
  if [ -f "$file" ]; then
    sed -i 's/maximum-pool-size: 10/maximum-pool-size: 100/g' "$file"
  fi
done
