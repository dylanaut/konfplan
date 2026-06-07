package kreyj.konfplan.infrastructure;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import kreyj.konfplan.domain.exception.BusinessException;
import org.jboss.logging.Logger;

import java.util.Map;

@Provider
public class BusinessExceptionMapper implements ExceptionMapper<BusinessException> {
    private static final Logger LOG = Logger.getLogger(BusinessExceptionMapper.class);


    @Override
    public Response toResponse(BusinessException exception) {
        LOG.warn("Bad Request: " + exception.getMessage());
        // Dieser eine Mapper fängt ALLE deine Business-Exceptions ab!
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", exception.getMessage()))
                .build();
    }
}