package kreyj.vortragsmanager.util;

import java.time.format.DateTimeFormatter;

public class DateHelper {
    private DateHelper() {
        // never instantiate
    }

    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
}
