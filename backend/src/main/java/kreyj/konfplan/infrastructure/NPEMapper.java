package kreyj.konfplan.infrastructure;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.Map;

@Provider
public class NPEMapper implements ExceptionMapper<NullPointerException> {
    private static final Logger LOG = Logger.getLogger(NPEMapper.class);


    @Override
    public Response toResponse(NullPointerException exception) {
        LOG.warn("NPE: " + exception.getMessage());

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", exception.getMessage()))
                .build();
    }
}