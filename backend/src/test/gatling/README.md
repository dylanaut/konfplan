# Performance Test

This directory contains the Gatling performance test for the Vortragsmanager application.

## Prerequisites

- Java 21
- Maven
- Application running on `http://localhost:9000`

## How to run the test

1.  Start the application: `mvn quarkus:dev`
2.  Run the test: `mvn gatling:test -Pperformance-test`
3.  The test results will be available in the `target/gatling` directory.

## Test Data

The test uses the `TeilnehmerFeeder.csv` file to get the test data. The file contains the following columns:

-   `username`
-   `password`

The test will randomly pick a user from the file and use it to log in and update the priorities.
