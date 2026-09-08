package kreyj.konfplan.infrastructure;

import jakarta.ws.rs.core.MediaType;
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
        // Dieser eine Mapper fängt ALLE deine Business-Exceptions ab! Media-Type explizit auf
        // JSON setzen: ohne .type(...) übernimmt die Antwort sonst das @Produces des
        // ausgelösten Endpunkts (z.B. TEXT_PLAIN bei /planungen/{vid}/dzn, application/zip bei
        // /planungen/{vid}/export) - die Map würde dann nicht als JSON, sondern über deren
        // toString() serialisiert (kein gültiges JSON, das Frontend kann es nicht parsen).
        return Response.status(Response.Status.BAD_REQUEST)
            .type(MediaType.APPLICATION_JSON)
            .entity(Map.of("error", exception.getMessage()))
            .build();
    }
}
