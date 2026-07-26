package kreyj.konfplan.persistence.converter;

import jakarta.persistence.AttributeConverter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

//@Converter(autoApply = true) // automatisch für alle LocalDateTime-Felder
public class LocalDateTimeConverter implements AttributeConverter<LocalDateTime, String> {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public String convertToDatabaseColumn(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(FORMATTER) : null;
    }

    @Override
    public LocalDateTime convertToEntityAttribute(String dbValue) {
        return dbValue != null ? LocalDateTime.parse(dbValue, FORMATTER) : null;
    }
}