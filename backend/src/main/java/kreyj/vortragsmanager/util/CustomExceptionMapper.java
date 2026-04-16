package kreyj.vortragsmanager.util;

import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

//@Provider
public class CustomExceptionMapper implements ExceptionMapper<Throwable> {

    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof UnauthorizedException) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Nicht autorisiert: Bitte melden Sie sich an.")
                    .build();
        }

        if (exception instanceof ForbiddenException) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("Zugriff verweigert: Unzureichende Berechtigungen.")
                    .build();
        }

        // Default mapping for other exceptions
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Ein unerwarteter Fehler ist aufgetreten: " + exception.getMessage())
                .build();
    }
}
