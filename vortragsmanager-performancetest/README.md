# Performance Test Module

This module contains the Gatling performance tests for the Vortragsmanager application.

## Prerequisites

- Java 21
- Maven
- The main application must be running on `http://localhost:9000`

## How to run the tests

1.  Navigate to the root directory of the `vortragsmanager` project.
2.  Start the backend application:
    ```bash
    cd backend
    mvn quarkus:dev
    ```
3.  In a new terminal, navigate to this module's directory (`vortragsmanager-performancetest`) and run the Gatling tests:
    ```bash
    mvn gatling:test
    ```
4.  The test report will be generated in the `target/gatling` directory within this module.

## Test Data

The test uses the `src/test/resources/TeilnehmerFeeder.csv` file for test data. The file contains the following columns:

-   `username`
-   `password`

The test will pick users from this file to simulate login and priority update actions.
