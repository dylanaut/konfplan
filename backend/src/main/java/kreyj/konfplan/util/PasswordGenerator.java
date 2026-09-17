package kreyj.konfplan.util;

import kreyj.konfplan.persistence.Passwortrichtlinie;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PasswordGenerator {
    // 0/O und 1/I/l entfernt (leicht zu verwechseln beim Abschreiben von einem Ausdruck)
    private static final String CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIAL = "!@#$%^&*-_=+?";
    // Volle Ziffernmenge (0/1 nicht ausgeschlossen) fuer ein reines Ziffern-PIN, wo keine
    // Verwechslungsgefahr mit Buchstaben besteht.
    private static final String ZIFFERN = "0123456789";
    private static final int DEFAULT_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordGenerator() {
        // never instantiate
    }


    public static String generate() {
        return generate(DEFAULT_LENGTH);
    }


    public static String generate(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }


    /**
     * Erzeugt ein Passwort, das die übergebene {@link Passwortrichtlinie} garantiert erfüllt
     * (siehe #741) - anders als {@link #generate()}/{@link #generate(int)}, die zwar ein reiches
     * Alphabet nutzen, aber keine Zeichenklasse explizit garantieren.
     */
    public static String generate(Passwortrichtlinie richtlinie) {
        int length = richtlinie.getMinLaenge();
        Integer maxLaenge = richtlinie.getMaxLaenge();
        if (null != maxLaenge && maxLaenge > length) {
            length += RANDOM.nextInt(maxLaenge - length + 1);
        }

        if (richtlinie.isNurZiffern()) {
            return generateFromAlphabet(ZIFFERN, length);
        }

        List<String> pflichtKlassen = new ArrayList<>();
        if (richtlinie.isErfordertGrossbuchstabe()) {
            pflichtKlassen.add(UPPER);
        }
        if (richtlinie.isErfordertKleinbuchstabe()) {
            pflichtKlassen.add(LOWER);
        }
        if (richtlinie.isErfordertZiffer()) {
            pflichtKlassen.add(DIGITS);
        }
        if (richtlinie.isErfordertSonderzeichen()) {
            pflichtKlassen.add(SPECIAL);
        }
        if (pflichtKlassen.isEmpty()) {
            return generateFromAlphabet(CHARS, length);
        }
        if (pflichtKlassen.size() > length) {
            throw new IllegalArgumentException("Mindestlänge reicht nicht für die geforderten Zeichenklassen aus.");
        }

        List<Character> passwort = new ArrayList<>(length);
        for (String klasse : pflichtKlassen) {
            passwort.add(klasse.charAt(RANDOM.nextInt(klasse.length())));
        }
        String gesamtAlphabet = String.join("", pflichtKlassen);
        while (passwort.size() < length) {
            passwort.add(gesamtAlphabet.charAt(RANDOM.nextInt(gesamtAlphabet.length())));
        }
        Collections.shuffle(passwort, RANDOM);

        StringBuilder sb = new StringBuilder(length);
        passwort.forEach(sb::append);
        return sb.toString();
    }


    private static String generateFromAlphabet(String alphabet, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
