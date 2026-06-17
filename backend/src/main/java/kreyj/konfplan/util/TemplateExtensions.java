package kreyj.konfplan.util;

import io.quarkus.qute.TemplateExtension;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@TemplateExtension
public class TemplateExtensions {
    private final static Pattern WORD_BOUNDARY_MATCHER = Pattern.compile("[- \t\n\r\f.,!?;:+()*]");


    private TemplateExtensions() {
        // never instantiate
    }


    public static String truncTo(String titel) {
        return truncTo(titel, 25);
    }


    public static String truncTo(String titel, int maxLen) {
        if (titel == null || titel.length() <= maxLen) {
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


    static String padded(String src, int len) {
        return padded(src, len, ' ');
    }


    static String padded(String src, int len, char ch) {
        return StringUtils.rightPad(src, len, ch);
    }


    static String join(List<String> list, String delimiter) {
        return String.join(delimiter, list);
    }


    static int add(int value, int amount) {
        return value + amount;
    }


    static int sub(int value, int amount) {
        return value - amount;
    }


    static float div(int dividend, int divisor) {
        return (float) dividend / divisor;
    }
}
