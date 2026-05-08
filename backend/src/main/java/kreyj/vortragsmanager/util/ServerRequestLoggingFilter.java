package kreyj.vortragsmanager.util;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

//@Provider // Registers the filter with JAX-RS
public class ServerRequestLoggingFilter extends LoggingFilterHelper implements ContainerRequestFilter {
    private static final Logger LOG = Logger.getLogger(ServerRequestLoggingFilter.class);

    @Override
    public void filter(ContainerRequestContext context) throws IOException {
        // Log basic request info
        LOG.debugf("Incoming Request: %s %s",
                context.getMethod(),
                context.getUriInfo().getRequestUri());

        // Log headers
        LOG.debug("Headers: " + context.getHeaders());

        // Log request body (if present and not binary)
        if (isTextualMediaType(context.getMediaType())) {
            String body = readBody(context.getEntityStream());
            // Reset the input stream so the resource can read it
            context.setEntityStream(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
            LOG.debug("Request Body: " + body);
        }
    }

    // Read the request body from the input stream
    private String readBody(InputStream inputStream) throws IOException {
        try (InputStream is = inputStream) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
