package kreyj.konfplan.util;

import org.apache.commons.lang3.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StringHelper {
    private static final Pattern DIGIT_PATTERN = Pattern.compile("(\\d+)|(\\D+)");
    public static final Comparator<String> NUM_OR_ALPHA_COMPARATOR =
        (s1, s2) -> {
            Matcher m1 = DIGIT_PATTERN.matcher(s1);
            Matcher m2 = DIGIT_PATTERN.matcher(s2);

            while (m1.find() && m2.find()) {
                // Beide Teile sind Zahlen -> Numerischer Vergleich
                if (m1.group(1) != null && m2.group(1) != null) {
                    int num1 = Integer.parseInt(m1.group(1));
                    int num2 = Integer.parseInt(m2.group(1));
                    if (num1 != num2) {
                        return Integer.compare(num1, num2);
                    }
                } else {
                    // Textanteil oder gemischter Vergleich
                    int cmp = m1.group().compareTo(m2.group());
                    if (cmp != 0) {
                        return cmp;
                    }
                }
            }
            return Integer.compare(s1.length(), s2.length());
        };


    private StringHelper() {
        // never instantiate
    }


    public static String fullname(String firstName, String lastName) {
        if (StringUtils.isBlank(firstName)) {
            if (StringUtils.isBlank(lastName)) {
                return "NONAME";
            } else {
                return lastName;
            }
        } else if (StringUtils.isBlank(lastName)) {
            return firstName;
        } else {
            return lastName + ", " + firstName;
        }
    }


    private record SortableName(String originalValue, String sortKey) implements Comparable<SortableName> {
        @Override
        public int compareTo(SortableName other) {
            // Der Vergleich ist jetzt trivial und schnell
            return this.sortKey.compareTo(other.sortKey);
        }
    }


    // entfernt diakritische Zeichen für "bessere" Sortierung'
    private static String purifiedName(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}", "");
    }


    public static List<String> sortNames(List<String> names) {
        List<SortableName> decoratedList = new ArrayList<>();
        for (String name : names) {
            decoratedList.add(new SortableName(name, purifiedName(name)));
        }

        // --- SORT ---
        // Die Sortierung verwendet den schnellen Vergleich der vorberechneten Schlüssel.
        Collections.sort(decoratedList);

        // --- UNDECORATE ---
        // Extrahieren der Originalwerte in der jetzt korrekten Reihenfolge.
        List<String> sortedNames = new ArrayList<>();
        for (SortableName name : decoratedList) {
            sortedNames.add(name.originalValue);
        }

        return sortedNames;
    }
}
