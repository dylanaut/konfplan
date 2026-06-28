package kreyj.konfplan.util;

import io.quarkus.qute.TemplateExtension;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * adding functions to Qute Template Engine
 */
@ApplicationScoped
@TemplateExtension
public class TemplateExtensions {
    private final static Pattern WORD_BOUNDARY_MATCHER = Pattern.compile("[- \t\n\r\f.,!?;:+()*]");


    public static String truncTo(String titel) {
        return truncTo(titel, 25);
    }


    public static String truncTo(String titel, int maxLen) {
        if (null == titel || titel.length() <= maxLen) {
            return titel;
        }

        String truncated = titel.substring(0, maxLen);

        int lastIndex = -1;
        Matcher matcher = WORD_BOUNDARY_MATCHER.matcher(truncated);

        while (matcher.find()) {
            lastIndex = matcher.start();
        }

        if (lastIndex > 0) {
            return truncated.substring(0, lastIndex).trim() + "...";
        } else {
            return truncated.trim() + "...";
        }
    }


    public static String padded(String src, int len) {
        return padded(src, len, ' ');
    }


    public static String padded(String src, int len, char ch) {
        return StringUtils.rightPad(src, len, ch);
    }


    public static String join(List<String> list, String delimiter) {
        return String.join(delimiter, list);
    }


    public static String slotRoomKey(Long slotId, long roomId) {
        return String.format("%d_%d", slotId, roomId);
    }


    public static int indexOf(long[] array, long value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == value) {
                return i;
            }
        }
        return -1;
    }


    public static boolean contains(long[] array, long value) {
        return indexOf(array, value) != -1;
    }


    public static int add(int value, int amount) {
        return value + amount;
    }


    public static int sub(int value, int amount) {
        return value - amount;
    }


    public static float div(int dividend, int divisor) {
        return (float) dividend / divisor;
    }
}
