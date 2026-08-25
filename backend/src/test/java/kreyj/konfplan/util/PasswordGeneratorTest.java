package kreyj.konfplan.util;

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
}
