package kreyj.konfplan.infrastructure;

import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import kreyj.konfplan.util.JwtHelper;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

/**
 * Rohe {@link WebApplicationException} (z.B. aus {@code PrioritaetService.savePrioritaeten})
 * tragen bereits eine eigene {@code Response} und werden deshalb NICHT vom
 * {@link BusinessExceptionMapper} abgefangen - ohne diesen Mapper landet zu solchen
 * Business-Validierungsfehlern kein einziger Log-Eintrag im Server-Log, was die Diagnose bei
 * Meldungen externer Nutzer (bei denen nur der loginName bekannt ist) unmöglich macht.
 */
@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {
    private static final Logger LOG = Logger.getLogger(WebApplicationExceptionMapper.class);

    @Inject
    JsonWebToken jwt;

    @Override
    public Response toResponse(WebApplicationException exception) {
        String loginName = null == jwt ? null : JwtHelper.getUserPrincipalName(jwt);
        LOG.warnf("HTTP %d fuer Nutzer '%s': %s",
            exception.getResponse().getStatus(), loginName, exception.getMessage());
        return exception.getResponse();
    }
}
