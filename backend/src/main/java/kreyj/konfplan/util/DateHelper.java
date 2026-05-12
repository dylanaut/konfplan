package kreyj.konfplan.util;

import java.time.format.DateTimeFormatter;

public final class DateHelper {
    private DateHelper() {
        // never instantiate
    }

    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
}
