package kreyj.konfplan.util;

import java.security.SecureRandom;

public final class PasswordGenerator {
    // 0/O und 1/I/l entfernt (leicht zu verwechseln beim Abschreiben von einem Ausdruck)
    private static final String CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
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
}
