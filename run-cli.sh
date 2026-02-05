#!/bin/bash
# Hotel Reservation CLI Runner
# Runs the interactive CLI without starting Spring Boot

# Build classpath
CP="target/classes"
for jar in ~/.m2/repository/ai/scop/scop-core/*/scop-core-*.jar; do CP="$CP:$jar"; done
for jar in ~/.m2/repository/ch/qos/logback/logback-classic/*/logback-classic-*.jar; do CP="$CP:$jar"; done
for jar in ~/.m2/repository/ch/qos/logback/logback-core/*/logback-core-*.jar; do CP="$CP:$jar"; done
for jar in ~/.m2/repository/org/slf4j/slf4j-api/*/slf4j-api-*.jar; do CP="$CP:$jar"; done
for jar in ~/.m2/repository/com/fasterxml/jackson/core/jackson-databind/*/jackson-databind-*.jar; do CP="$CP:$jar"; done
for jar in ~/.m2/repository/com/fasterxml/jackson/core/jackson-core/*/jackson-core-*.jar; do CP="$CP:$jar"; done
for jar in ~/.m2/repository/com/fasterxml/jackson/core/jackson-annotations/*/jackson-annotations-*.jar; do CP="$CP:$jar"; done
for jar in ~/.m2/repository/io/github/cdimascio/dotenv-java/*/dotenv-java-*.jar; do CP="$CP:$jar"; done

# Run CLI (skip Spring Boot)
java -Dspring.main.web-application-type=none \
     -Dspring.main.banner-mode=off \
     -cp "$CP" \
     hotel.reservation.cli.HotelReservationCLI "$@"
