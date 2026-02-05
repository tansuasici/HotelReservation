#!/bin/bash
# Hotel Reservation CLI - connects to REST API server
cd "$(dirname "$0")"
java -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
     hotel.reservation.cli.HotelReservationCLI
