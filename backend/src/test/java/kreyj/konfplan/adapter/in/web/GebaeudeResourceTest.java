package kreyj.konfplan.adapter.in.web;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Gebaeudetyp;
import kreyj.konfplan.persistence.Raum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.CREATED;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;

/**
 * Regression-Tests fuer #424: createRaum/updateRaum sowie Gebaeude create/getOne/update gaben
 * bisher die rohe JPA-Entity zurueck. Beim Serialisieren (bzw. bei Gebaeude.update() schon beim
 * Zugriff auf die lazy raeume-Collection nach Abschluss der Transaktion) fuehrte das zu einer
 * LazyInitializationException (HTTP 500) bzw. bei createRaum sogar zu einem
 * PropertyValueException beim Persistieren, obwohl der eigentliche Datenbank-Schreibvorgang bei
 * updateRaum bereits erfolgreich war - fuer den Nutzer sichtbar als "Speichern tut scheinbar
 * nichts" ohne Fehlermeldung.
 */
@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
@TestSecurity(user = "organisator@test.de", roles = "ORGANISATOR")
@TestHTTPEndpoint(GebaeudeResource.class)
class GebaeudeResourceTest extends DatabaseCleaner {

    Long gebaeudeId;
    Long raumId;


    @BeforeEach
    @Transactional
    void setup() {
        Gebaeude gebaeude = new Gebaeude();
        gebaeude.setName("Haupthaus");
        gebaeude.setTyp(Gebaeudetyp.SCHULE);
        gebaeude.setOrt("Teststadt");
        gebaeude.setStrasse("Teststraße");
        gebaeude.setHausnummer("1");
        gebaeude.setPostleitzahl("12345");
        gebaeude.persist();
        gebaeudeId = gebaeude.getId();

        Raum raum = new Raum("Raum 1", 10);
        gebaeude.addRaum(raum);
        raum.persist();
        raumId = raum.getId();
    }


    @Test
    void getOne_liefertGebaeudeMitRaeumenOhneFehler() {
        given()
            .when().get("/{id}", gebaeudeId)
            .then()
            .statusCode(OK.getStatusCode())
            .body("name", is("Haupthaus"))
            .body("raeume.size()", is(1))
            .body("raeume[0].kapazitaet", is(10));
    }


    @Test
    void update_aendertNamenUndLiefertKorrektesGebaeudeDto() {
        String body = "{\"id\":" + gebaeudeId + ",\"version\":0,\"name\":\"Neues Haus\",\"typ\":\"SCHULE\",\"ort\":\"Teststadt\","
            + "\"strasse\":\"Teststraße\",\"hausnummer\":\"1\",\"postleitzahl\":\"12345\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().put("/{id}", gebaeudeId)
            .then()
            .statusCode(OK.getStatusCode())
            .body("name", is("Neues Haus"))
            .body("raeume.size()", is(1));

        assertThat(Gebaeude.<Gebaeude>findById(gebaeudeId).getName()).isEqualTo("Neues Haus");
    }


    @Test
    void updateRaum_speichertGeaenderteKapazitaetTatsaechlich() {
        String body = "{\"id\":" + raumId + ",\"version\":0,\"name\":\"Raum 1\",\"kapazitaet\":42,\"etage\":\"EG\","
            + "\"gebaeude\":{\"id\":" + gebaeudeId + "},\"verfuegbareSlots\":[]}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().put("/{gid}/raeume/{rid}", gebaeudeId, raumId)
            .then()
            .statusCode(OK.getStatusCode())
            .body("kapazitaet", is(42))
            .body("gebaeudeId", is(gebaeudeId.intValue()));

        assertThat(Raum.<Raum>findById(raumId).getKapazitaet()).isEqualTo(42);
    }


    @Test
    void createRaum_mitDetachedGebaeudeStubImRequestBody_funktioniert() {
        String body = "{\"name\":\"Neuer Raum\",\"kapazitaet\":5,\"etage\":\"1.OG\",\"gebaeude\":{\"id\":" + gebaeudeId + "}}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post("/{gid}/raeume", gebaeudeId)
            .then()
            .statusCode(CREATED.getStatusCode())
            .body("name", is("Neuer Raum"))
            .body("gebaeudeId", is(gebaeudeId.intValue()));

        assertThat(Raum.<Raum>list("gebaeude.id = ?1", gebaeudeId)).hasSize(2);
    }
}
