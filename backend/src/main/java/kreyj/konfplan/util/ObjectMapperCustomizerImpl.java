package kreyj.konfplan.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ObjectMapperCustomizerImpl implements ObjectMapperCustomizer {
    @Override
    public void customize(ObjectMapper objectMapper) {
        // Register the Blackbird module for optimized serialization/deserialization
        objectMapper.registerModule(new BlackbirdModule());
    }
}
