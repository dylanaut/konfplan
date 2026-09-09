package kreyj.konfplan.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Schreibt/liest {@code boolean[][]}- und {@code boolean[][][]}-Arrays als 0/1 statt
 * true/false, um beim Speichern großer MiniZinc-Ergebnisse (z.B.
 * {@link kreyj.konfplan.persistence.Planungsergebnis.MinizincResult#besucht}) Platz zu sparen.
 */
public class CompactBooleanArrayModule extends SimpleModule {

    public CompactBooleanArrayModule() {
        addSerializer(boolean[][].class, new Boolean2DArraySerializer());
        addSerializer(boolean[][][].class, new Boolean3DArraySerializer());
        addDeserializer(boolean[][].class, new Boolean2DArrayDeserializer());
        addDeserializer(boolean[][][].class, new Boolean3DArrayDeserializer());
    }


    private static void writeRow(JsonGenerator gen, boolean[] row) throws IOException {
        gen.writeStartArray();
        for (boolean value : row) {
            gen.writeNumber(value ? 1 : 0);
        }
        gen.writeEndArray();
    }


    private static void expectArrayStart(JsonParser p, DeserializationContext ctxt, Class<?> targetType) throws IOException {
        if (p.currentToken() != JsonToken.START_ARRAY) {
            throw ctxt.wrongTokenException(p, targetType, JsonToken.START_ARRAY, null);
        }
    }


    private static boolean[] readRow(JsonParser p, DeserializationContext ctxt) throws IOException {
        expectArrayStart(p, ctxt, boolean[].class);
        List<Boolean> values = new ArrayList<>();
        while (p.nextToken() != JsonToken.END_ARRAY) {
            values.add(p.getIntValue() != 0);
        }
        boolean[] row = new boolean[values.size()];
        for (int i = 0; i < row.length; i++) {
            row[i] = values.get(i);
        }
        return row;
    }


    private static class Boolean2DArraySerializer extends JsonSerializer<boolean[][]> {
        @Override
        public void serialize(boolean[][] value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartArray();
            for (boolean[] row : value) {
                writeRow(gen, row);
            }
            gen.writeEndArray();
        }
    }


    private static class Boolean3DArraySerializer extends JsonSerializer<boolean[][][]> {
        @Override
        public void serialize(boolean[][][] value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartArray();
            for (boolean[][] plane : value) {
                gen.writeStartArray();
                for (boolean[] row : plane) {
                    writeRow(gen, row);
                }
                gen.writeEndArray();
            }
            gen.writeEndArray();
        }
    }


    private static class Boolean2DArrayDeserializer extends JsonDeserializer<boolean[][]> {
        @Override
        public boolean[][] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            expectArrayStart(p, ctxt, boolean[][].class);
            List<boolean[]> rows = new ArrayList<>();
            while (p.nextToken() != JsonToken.END_ARRAY) {
                rows.add(readRow(p, ctxt));
            }
            return rows.toArray(new boolean[0][]);
        }
    }


    private static class Boolean3DArrayDeserializer extends JsonDeserializer<boolean[][][]> {
        @Override
        public boolean[][][] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            expectArrayStart(p, ctxt, boolean[][][].class);
            List<boolean[][]> planes = new ArrayList<>();
            while (p.nextToken() != JsonToken.END_ARRAY) {
                expectArrayStart(p, ctxt, boolean[][].class);
                List<boolean[]> rows = new ArrayList<>();
                while (p.nextToken() != JsonToken.END_ARRAY) {
                    rows.add(readRow(p, ctxt));
                }
                planes.add(rows.toArray(new boolean[0][]));
            }
            return planes.toArray(new boolean[0][][]);
        }
    }
}
