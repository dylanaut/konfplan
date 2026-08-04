package kreyj.konfplan.performancetest

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * Validiert, dass das Backend eine feste Anzahl GLEICHZEITIGER Nutzer bedienen kann (geschlossenes
 * Lastmodell: Gatling haelt konstant so viele aktive virtuelle Nutzer, wie konfiguriert, und
 * startet sofort einen neuen, sobald einer fertig ist) - anders als TeilnehmerSimulations
 * Ankunftsraten-Modell, das die Anzahl gleichzeitiger Nutzer nur indirekt ueber Ankunftsrate x
 * Sitzungsdauer ergibt.
 *
 * Nutzung: mvn gatling:test -Dgatling.simulationClass=kreyj.konfplan.performancetest.ConcurrencyCapacitySimulation
 *          [-DconcurrentUsers=700] [-DrampUpSeconds=60] [-DholdSeconds=120]
 */
class ConcurrencyCapacitySimulation extends Simulation {

  val targetConcurrentUsers: Int = Integer.getInteger("concurrentUsers", 700)
  val rampUpSeconds: Int = Integer.getInteger("rampUpSeconds", 60)
  val holdSeconds: Int = Integer.getInteger("holdSeconds", 120)

  val httpProtocol = http
    .baseUrl("http://localhost:9000")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  setUp(
    TeilnehmerScenario.scn.inject(
      rampConcurrentUsers(0) to targetConcurrentUsers during (rampUpSeconds.seconds),
      constantConcurrentUsers(targetConcurrentUsers) during (holdSeconds.seconds)
    )
  ).protocols(httpProtocol)
    .assertions(
      global.failedRequests.percent.lt(1),
      global.responseTime.percentile(95).lt(3000)
    )
}
