package kreyj.vortragsmanager.util;

import jakarta.ws.rs.core.MediaType;

public class LoggingFilterHelper {
    // Check if media type is text-based (e.g., JSON, XML)
    public static boolean isTextualMediaType(MediaType mediaType) {
        if (mediaType != null) {
            if (mediaType.getType().equals("text")) {
                return true;
            }

            if (null != mediaType.getSubtype()) {
                return mediaType.getSubtype().endsWith("json")
                        || mediaType.getSubtype().endsWith("xml");
            }
        }
        return false;
    }
}
