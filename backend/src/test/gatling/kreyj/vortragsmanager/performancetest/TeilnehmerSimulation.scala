package kreyj.vortragsmanager.performancetest

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class TeilnehmerSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:9000")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  val benutzerFeeder = csv("TeilnehmerFeeder.csv").random

  val loginSzenario = scenario("Login")
    .feed(benutzerFeeder)
    .exec(
      http("Login")
        .post("/api/auth/login")
        .body(StringBody("""{ "username": "${username}", "password": "${password}" }"""))
        .asJson
        .check(status.is(200))
        .check(jsonPath("$.token").saveAs("jwtToken"))
    )

  val prioUpdateSzenario = scenario("Prioritaeten aktualisieren")
    .exec(
      http("Prioritaeten aktualisieren")
        .post("/api/teilnehmer/prios")
        .header("Authorization", "Bearer ${jwtToken}")
        .body(StringBody("""[
          { "vortragId": 1, "prioritaet": 1 },
          { "vortragId": 2, "prioritaet": 2 },
          { "vortragId": 3, "prioritaet": 3 }
        ]"""))
        .asJson
        .check(status.is(200))
    )

  setUp(
    loginSzenario.inject(
      rampUsers(10) during (10 seconds)
    ),
    prioUpdateSzenario.inject(
      rampUsers(100) during (60 seconds),
      constantUsersPerSec(100) during (5 minutes),
      rampUsers(0) during (60 seconds)
    )
  ).protocols(httpProtocol)
}
