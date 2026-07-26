package kreyj.konfplan.util;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class DateHelper {
    private DateHelper() {
        // never instantiate
    }


    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm:ss ");

    public static final DateTimeFormatter DAY_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd.MM.yy", Locale.GERMAN);
    public static final DateTimeFormatter HOUR_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN);
}
