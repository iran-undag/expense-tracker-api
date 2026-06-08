#!/bin/bash

# Local development server startup script
# This script loads environment variables from .env and runs the Spring Boot application in dev mode

set -e

if [ ! -f .env ]; then
    echo "Error: .env file not found. Please copy .env.sample to .env and configure it."
    exit 1
fi

echo "Loading environment variables from .env..."
set -a
source .env
set +a

echo "Starting Expense Tracker API in development mode..."
echo "API will be available at http://localhost:8081"
echo ""

./mvnw spring-boot:run \
    -Dspring-boot.run.profiles=dev \
    -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=dev"
