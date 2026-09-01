package kreyj.konfplan.presentation;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@PermitAll
@Path("/testmail")
public class DemoMailResource {
    @Inject
    Mailer mailer;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String sendTestMail() {
        mailer.send(Mail.withText("test_recipient@example.com",
                        "Test-E-Mail von Quarkus",
                        "Dies ist eine Test-E-Mail, gesendet von Ihrer Quarkus-Anwendung.")
                .setFrom("juergenkrey@yahoo.de")
                .setReplyTo("konfplan@yahoo.com"));

        return "Test-E-Mail gesendet!";
    }
}
