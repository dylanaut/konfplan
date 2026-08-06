package kreyj.konfplan.performancetest

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * Reguläre Lasttest-Kampagne mit realistischem Ankunftsraten-Profil (Ramp-up/Peak/Ramp-down).
 * Das eigentliche Nutzerszenario steckt in [[TeilnehmerScenario]] (gemeinsam genutzt mit
 * [[ConcurrencyCapacitySimulation]]).
 */
class TeilnehmerSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:9000")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  // A load profile with ramp-up, peak load, and ramp-down phases
  setUp(
    TeilnehmerScenario.scn.inject(
      rampUsersPerSec(1) to 10 during(2.minutes), // Ramp-up phase
      constantUsersPerSec(10) during(5.minutes),   // Peak load phase
      rampUsersPerSec(10) to 1 during(2.minutes)  // Ramp-down phase
    )
  ).protocols(httpProtocol)
}
