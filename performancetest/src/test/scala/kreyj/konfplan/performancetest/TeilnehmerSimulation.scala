package kreyj.konfplan.performancetest

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class TeilnehmerSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:9000")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  // Use circular strategy so users are reused during long tests
  val benutzerFeeder = csv("TeilnehmerFeeder.csv").circular

  // A realistic user scenario: login, view priorities, then update them
  val scn = scenario("Teilnehmer Dashboard Interaktion")
    .feed(benutzerFeeder)
    .exec(
      http("1. Login als Teilnehmer")
        .post("/api/auth/login")
        .body(StringBody("""{ "username": "${username}", "password": "${password}" }""")).asJson
        .check(status.is(200))
        .check(jsonPath("$.token").saveAs("jwtToken"))
    )
    .pause(1.second, 5.seconds) // Simulate user think time
    .exec(
      http("2. Prioritäten laden")
        .get("/api/prios/" + "${vid}")
        .header("Authorization", "Bearer ${jwtToken}")
        .check(status.is(200))
    )
    .pause(5.seconds, 10.seconds) // Simulate user think time before updating
    .exec(
      http("3. Prioritäten aktualisieren")
        .post("/api/prios")
        .header("Authorization", "Bearer ${jwtToken}")
        .body(StringBody("""[
          { "vortragId": 1, "prioritaet": 1 },
          { "vortragId": 2, "prioritaet": 2 },
          { "vortragId": 3, "prioritaet": 3 }
        ]""")).asJson
        .check(status.is(200))
    )

  // A load profile with ramp-up, peak load, and ramp-down phases
  setUp(
    scn.inject(
      rampUsersPerSec(1) to 10 during(2.minutes), // Ramp-up phase
      constantUsersPerSec(10) during(5.minutes),   // Peak load phase
      rampUsersPerSec(10) to 1 during(2.minutes)  // Ramp-down phase
    )
  ).protocols(httpProtocol)
}
