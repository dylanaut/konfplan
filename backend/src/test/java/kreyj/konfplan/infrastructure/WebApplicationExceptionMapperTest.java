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
    void toResponse_traegtValidierungstextAlsEntityNach() {
        WebApplicationExceptionMapper mapper = new WebApplicationExceptionMapper();
        mapper.jwt = mock(JsonWebToken.class);
        when(mapper.jwt.getClaim("preferred_username")).thenReturn("tom.teilnehmer");

        WebApplicationException exception = new WebApplicationException("Es dürfen höchstens 3 Prioritäten vergeben werden",
            Response.Status.BAD_REQUEST.getStatusCode());
        // Der (String, int)-Konstruktor baut laut JAX-RS-Spec eine Response OHNE Entity - nur
        // exception.getMessage() traegt den Text, siehe Mapper-Javadoc.
        assertThat(exception.getResponse().hasEntity()).isFalse();

        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(response.getEntity()).isEqualTo("Es dürfen höchstens 3 Prioritäten vergeben werden");
    }

    @Test
    void toResponse_responseHatBereitsEntity_wirdUnveraendertDurchgereicht() {
        WebApplicationExceptionMapper mapper = new WebApplicationExceptionMapper();
        mapper.jwt = mock(JsonWebToken.class);
        when(mapper.jwt.getClaim("preferred_username")).thenReturn("tom.teilnehmer");

        Response withEntity = Response.status(Response.Status.CONFLICT).entity("bereits vorhandene Meldung").build();
        WebApplicationException exception = new WebApplicationException("ignoriert", withEntity);

        Response response = mapper.toResponse(exception);

        assertThat(response).isSameAs(withEntity);
        assertThat(response.getEntity()).isEqualTo("bereits vorhandene Meldung");
    }

    @Test
    void toResponse_ohneJwt_wirftKeineException() {
        WebApplicationExceptionMapper mapper = new WebApplicationExceptionMapper();
        mapper.jwt = null;

        WebApplicationException exception = new WebApplicationException("Referent not found", Response.Status.NOT_FOUND.getStatusCode());

        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
        assertThat(response.getEntity()).isEqualTo("Referent not found");
    }
}
