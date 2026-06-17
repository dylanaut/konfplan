package kreyj.konfplan.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NameSorting {
    private NameSorting() {
        // never instantiate
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
