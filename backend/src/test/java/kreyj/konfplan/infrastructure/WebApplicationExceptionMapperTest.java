package kreyj.konfplan.infrastructure;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebApplicationExceptionMapperTest {

    @Test
    void toResponse_liefertUnveraenderteResponseDerException() {
        WebApplicationExceptionMapper mapper = new WebApplicationExceptionMapper();
        mapper.jwt = mock(JsonWebToken.class);
        when(mapper.jwt.getClaim("preferred_username")).thenReturn("tom.teilnehmer");

        WebApplicationException exception = new WebApplicationException("Jede Priorität darf nur einmal vergeben werden",
            Response.Status.BAD_REQUEST);

        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(response).isSameAs(exception.getResponse());
    }

    @Test
    void toResponse_ohneJwt_wirftKeineException() {
        WebApplicationExceptionMapper mapper = new WebApplicationExceptionMapper();
        mapper.jwt = null;

        WebApplicationException exception = new WebApplicationException("Referent not found", Response.Status.NOT_FOUND);

        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
    }
}
