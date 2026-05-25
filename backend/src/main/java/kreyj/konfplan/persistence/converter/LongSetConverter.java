package kreyj.konfplan.persistence.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.HashSet;
import java.util.Set;

@Converter
public class LongSetConverter implements AttributeConverter<Set<Long>, String> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Set<Long> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return mapper.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalArgumentException("Set<Long> konnte nicht serialisiert werden", e);
        }
    }

    @Override
    public Set<Long> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new HashSet<>();
        }
        try {
            return mapper.readValue(dbData,
                    mapper.getTypeFactory().constructCollectionType(Set.class, Long.class));
        } catch (Exception e) {
            throw new IllegalArgumentException("Set<Long> konnte nicht deserialisiert werden", e);
        }
    }
}