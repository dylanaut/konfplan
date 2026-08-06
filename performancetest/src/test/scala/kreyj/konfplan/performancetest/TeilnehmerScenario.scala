package kreyj.konfplan.performancetest

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * Gemeinsamer Szenario-Baustein (Login + Teilnehmer-Dashboard-Ablauf), von mehreren
 * Simulationen genutzt: [[TeilnehmerSimulation]] fuer die reguläre Lasttest-Kampagne und
 * [[ConcurrencyCapacitySimulation]] zur gezielten Validierung einer bestimmten Anzahl
 * gleichzeitiger Nutzer (geschlossenes Lastmodell statt Ankunftsrate).
 */
object TeilnehmerScenario {

  // Use circular strategy so users are reused during long tests.
  // Die Zugangsdaten (loginName/password) stammen aus dem karrierekompass-linz-2026
  // Dev-Datensatz (backend/src/test/resources/csv_import/karrierekompass-linz-2026/teilnehmer.csv);
  // Standard-Passwort aller CSV-importierten Nutzer im Dev/Test-Modus ist "konfplan"
  // (TeilnehmerService.importFromCsv - im PROD-Profil waere es stattdessen ein
  // zufaelliges UUID-Passwort, das hier also nicht funktionieren wuerde).
  // Zum Testen muss das Backend mit diesem Datensatz geladen sein, z.B. via
  // "cd backend && ../mvnw quarkus:dev" (Default-Konfiguration in application.properties).
  val benutzerFeeder = csv("TeilnehmerFeeder.csv").circular

  // Alle dynamischen Werte (Session-Daten) werden ueber Session=>String Funktionen statt
  // der "${var}"-EL-Platzhalter-Syntax eingesetzt: Mit der in diesem Projekt verwendeten
  // Gatling-Version (3.15.0) wird "${var}" in StringBody/header/get NICHT interpoliert
  // (per Netcat-Mitschnitt verifiziert - der literale Platzhalter kam unveraendert beim
  // Server an), waehrend Session=>String-Funktionen zuverlaessig funktionieren.

  def loginBody(session: Session): String = {
    val loginName = session("loginName").as[String]
    val password = session("password").as[String]
    s"""{ "loginName": "$loginName", "password": "$password" }"""
  }

  def authHeader(session: Session): String = "Bearer " + session("jwtToken").as[String]

  def vortraegePath(session: Session): String = s"/api/teilnehmer/veranstaltungen/${session("vid").as[String]}/vortraege"

  def priosPath(session: Session): String = s"/api/prios/${session("vid").as[String]}"

  def zuweisungenPath(session: Session): String = s"/api/teilnehmer/veranstaltungen/${session("vid").as[String]}/zuweisungen"

  // Baut aus den zuvor geladenen Wahlvortrag-IDs eine Prioritaeten-Liste: die ersten
  // bis zu 10 Wahlvortraege bekommen absteigend eindeutige prioWert-Werte (10=hoechste,
  // siehe Prioritaet.PRIO_MIN/PRIO_MAX) - der Server lehnt doppelte prioWert-Werte in
  // einem Request ab.
  def prioritaetenBody(session: Session): String = {
    val wahlvortragIds = session("wahlvortragIds").asOption[Seq[Long]].getOrElse(Seq.empty)
    val prioWerte = 10 to 1 by -1
    val eintraege = wahlvortragIds.take(prioWerte.length).zip(prioWerte).map { case (vortragId, prioWert) =>
      s"""{ "vortragId": $vortragId, "prioWert": $prioWert }"""
    }
    eintraege.mkString("[", ",", "]")
  }

  // Realistischer Nutzerablauf, nachgebildet aus TeilnehmerDashboard.vue (onMounted +
  // togglePriorities): Login, eigene Veranstaltungen laden, dann Vortraege/Prioritaeten/
  // Plan parallel laden (wie beim Aufklappen der Prioritaeten-Ansicht im Frontend) und
  // schliesslich die Prioritaeten speichern.
  val scn = scenario("Teilnehmer Dashboard Interaktion")
    .feed(benutzerFeeder)
    .exec(
      http("1. Login als Teilnehmer")
        .post("/api/auth/login")
        .body(StringBody(loginBody _))
        .check(status.is(200))
        .check(jsonPath("$.token").saveAs("jwtToken"))
    )
    .pause(1.second, 5.seconds) // Simulate user think time
    .exec(
      http("2. Eigene Veranstaltungen laden")
        .get("/api/teilnehmer/veranstaltungen")
        .header("Authorization", authHeader _)
        .check(status.is(200))
        .check(jsonPath("$[0].id").saveAs("vid"))
    )
    .pause(1.second, 3.seconds)
    .exec(
      http("3. Meine Vortraege laden")
        .get(vortraegePath _)
        .header("Authorization", authHeader _)
        .check(status.is(200))
        .check(jsonPath("$[?(@.istPflicht == false)].id").ofType[Long].findAll.saveAs("wahlvortragIds"))
        .resources(
          http("3b. Prioritaeten laden")
            .get(priosPath _)
            .header("Authorization", authHeader _)
            .check(status.is(200)),
          http("3c. Persoenlichen Plan laden")
            .get(zuweisungenPath _)
            .header("Authorization", authHeader _)
            .check(status.is(200))
        )
    )
    .pause(5.seconds, 10.seconds) // Simulate user think time before updating
    .exec(
      http("4. Prioritaeten aktualisieren")
        .post("/api/prios")
        .header("Authorization", authHeader _)
        .body(StringBody(prioritaetenBody _))
        .check(status.is(200))
    )
}
