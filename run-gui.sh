#!/bin/bash
# Hotel Reservation GUI - SCOP UI with NetworkEnvironmentPanel
# Starts Spring Boot (for Hotel Data API) then launches SCOP Swing GUI

cd "$(dirname "$0")"

SPRING_PID=""

cleanup() {
    echo "[GUI] Shutting down..."
    if [ -n "$SPRING_PID" ] && kill -0 "$SPRING_PID" 2>/dev/null; then
        kill "$SPRING_PID" 2>/dev/null
        wait "$SPRING_PID" 2>/dev/null
        echo "[GUI] Spring Boot stopped."
    fi
    exit 0
}
trap cleanup EXIT INT TERM

# 1. Build
echo "[GUI] Building project..."
mvn clean compile -q
if [ $? -ne 0 ]; then
    echo "[GUI] Build failed!"
    exit 1
fi

# 2. Build classpath
CP="target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)"

# 3. Start Spring Boot in background (for Hotel Data API)
echo "[GUI] Starting Hotel Data API (Spring Boot)..."
java -cp "$CP" hotel.reservation.api.HotelReservationApplication &
SPRING_PID=$!

# 4. Wait for Spring Boot to be ready
echo "[GUI] Waiting for API to be ready on port 8080..."
for i in $(seq 1 30); do
    if curl -s http://localhost:8080/api/data/hotels > /dev/null 2>&1; then
        echo "[GUI] API is ready!"
        break
    fi
    if ! kill -0 "$SPRING_PID" 2>/dev/null; then
        echo "[GUI] Spring Boot failed to start!"
        exit 1
    fi
    sleep 1
done

# 5. Launch SCOP GUI
echo "[GUI] Launching SCOP UI..."
java -cp "$CP" ai.scop.ui.Main exec-gui-swing --config=src/main/resources/config.json

echo "[GUI] GUI closed."
