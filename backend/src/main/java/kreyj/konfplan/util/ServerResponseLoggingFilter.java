package kreyj.konfplan.util;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import org.jboss.logging.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static kreyj.konfplan.util.BaseLoggingFilter.isTextualMediaType;

//@Provider
public class ServerResponseLoggingFilter implements ContainerResponseFilter {
    private static final Logger LOG = Logger.getLogger(ServerResponseLoggingFilter.class);

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        // Log status and headers
        LOG.debugf("Outgoing Response: Status %d", responseContext.getStatus());
        LOG.debug("Response Headers: " + responseContext.getHeaders());

        // Log response body (if text-based)
        if (isTextualMediaType(responseContext.getMediaType())) {
            // Wrap the output stream to capture the response body
            OutputStream originalStream = responseContext.getEntityStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            responseContext.setEntityStream(baos);

            // After the response is written, log the body
            responseContext.setEntityStream(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    baos.write(b);
                    originalStream.write(b);
                }

                @Override
                public void flush() throws IOException {
                    baos.flush();
                    originalStream.flush();
                    LOG.debug("Response Body: " + baos.toString(StandardCharsets.UTF_8));
                }
            });
        }
    }
}
