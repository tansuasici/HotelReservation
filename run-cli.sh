#!/bin/bash
# Hotel Reservation CLI Runner
# Runs the interactive CLI without starting Spring Boot

cd "$(dirname "$0")"

# Build classpath using Maven (resolves all dependencies correctly)
CP="target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)"

# Run CLI
java -cp "$CP" hotel.reservation.cli.HotelReservationCLI "$@"
