# Performance Test Module

This module contains the Gatling performance tests for the KonfPlan application.

## Prerequisites

- Java 21
- Maven
- The main application must be running on `http://localhost:9000`, seeded with the
  `karrierekompass-linz-2026` dev dataset (the default `%dev.konfplan.dev-data.datasets`
  in `application.properties` already includes it) - the feeder CSV references real
  Teilnehmer accounts from that dataset.

## How to run the tests

1.  Navigate to the root directory of the `konfplan` project.
2.  Start the backend application:
    ```bash
    cd backend
    mvn quarkus:dev
    ```
3.  In a new terminal, navigate to this module's directory (`konfplan-performancetest`) and run the Gatling tests:
    ```bash
    mvn gatling:test
    ```
4.  The test report will be generated in the `target/gatling` directory within this module.

## Test Data

The test uses the `src/test/resources/TeilnehmerFeeder.csv` file for test data. The file contains the following columns:

-   `loginName` - matches the `LoginName` column of `backend/src/test/resources/csv_import/karrierekompass-linz-2026/teilnehmer.csv`
-   `password` - the default password for CSV-imported users when running in dev/test mode, `konfplan` (see `TeilnehmerService.importFromCsv`; production imports get a random UUID password instead, so this feeder only works against a `quarkus:dev` instance)

The test will pick users from this file to simulate login, viewing talks/priorities/personal
plan, and updating priorities.

## Gotcha: no `"${var}"` EL strings

With the Gatling/Scala versions pinned in this module's `pom.xml`, Gatling's `${var}` EL
placeholder syntax is not interpolated in `StringBody`, `.header(...)`, or `.get(...)` string
literals - the literal `${var}` text is sent as-is (verified with a raw TCP capture). All
dynamic values in `TeilnehmerSimulation.scala` are therefore built via `Session => String`
functions instead (e.g. `.body(StringBody(loginBody _))`, `.header("Authorization", authHeader _)`).
Keep new dynamic request parts in that style rather than reintroducing `"${var}"` strings.
