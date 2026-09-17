package kreyj.konfplan.util;

import kreyj.konfplan.persistence.Passwortrichtlinie;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordGeneratorTest {

    @Test
    void generatesDefaultLengthOfTen() {
        assertThat(PasswordGenerator.generate()).hasSize(10);
    }


    @Test
    void generatesRequestedLength() {
        assertThat(PasswordGenerator.generate(6)).hasSize(6);
    }


    @Test
    void neverContainsAmbiguousCharacters() {
        for (int i = 0; i < 200; i++) {
            String password = PasswordGenerator.generate();
            assertThat(password).doesNotContainAnyWhitespaces();
            for (char forbidden : new char[]{'0', 'O', '1', 'I', 'l'}) {
                assertThat(password).doesNotContain(String.valueOf(forbidden));
            }
        }
    }


    @Test
    void producesNoDuplicatesInLargeSample() {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            generated.add(PasswordGenerator.generate());
        }
        assertThat(generated).hasSize(5000);
    }


    @Test
    void generateMitRichtlinie_juniorPin_erzeugtStetsKonformesPasswort() {
        Passwortrichtlinie juniorPin = new Passwortrichtlinie(null, "TEILNEHMER", 6, 6, false, false, false, false, true);
        for (int i = 0; i < 200; i++) {
            String passwort = PasswordGenerator.generate(juniorPin);
            assertThat(juniorPin.erfuellt(passwort)).isTrue();
        }
    }


    @Test
    void generateMitRichtlinie_standard_erzeugtStetsKonformesPasswort() {
        for (int i = 0; i < 200; i++) {
            String passwort = PasswordGenerator.generate(Passwortrichtlinie.STANDARD);
            assertThat(Passwortrichtlinie.STANDARD.erfuellt(passwort)).isTrue();
        }
    }


    @Test
    void generateMitRichtlinie_variableLaenge_bleibtImBereich() {
        Passwortrichtlinie richtlinie = new Passwortrichtlinie(null, "TEILNEHMER", 4, 8, false, false, true, false, true);
        for (int i = 0; i < 200; i++) {
            String passwort = PasswordGenerator.generate(richtlinie);
            assertThat(passwort.length()).isBetween(4, 8);
            assertThat(richtlinie.erfuellt(passwort)).isTrue();
        }
    }
}
