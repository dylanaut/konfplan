package kreyj.vortragsmanager.resource;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.mailer.MailTemplate;
import io.quarkus.qute.Location;
import io.smallrye.jwt.build.Jwt;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import kreyj.vortragsmanager.dto.LoginRequest;
import kreyj.vortragsmanager.dto.ResetRequest;
import kreyj.vortragsmanager.dto.TokenResponse;
import kreyj.vortragsmanager.entity.Nutzer;
import kreyj.vortragsmanager.entity.ProtokollKategorie;
import kreyj.vortragsmanager.service.ProtokollService;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Path("/api/auth")
public class AuthResource {
    @Inject
    @Location("passwordReset")
    MailTemplate passwordResetTemplate;

    @Inject
    JsonWebToken jwt;

    @Inject
    ProtokollService protokollService;

    @POST
    @Path("/forgot-password")
    @PermitAll
    @Transactional
    public Response forgotPassword(@QueryParam("email") String email) {
        Nutzer nutzer = Nutzer.findByEmail(email);

        if (nutzer != null) {
            String token = UUID.randomUUID().toString();
            nutzer.resetToken = token;
            nutzer.resetTokenExpiry = LocalDateTime.now().plusHours(2);
            nutzer.persist();

            String resetUrl = "http://localhost:5173/reset-password?token=" + token;

            passwordResetTemplate.to(nutzer.email)
                    .subject("Passwort zurücksetzen - Vortragsmanager")
                    .data("firstName", nutzer.firstName)
                    .data("resetLink", resetUrl)
                    .send()
                    .subscribe().with(
                            success -> System.out.println("Mail gesendet an " + nutzer.email),
                            failure -> System.err.println("Mail-Fehler: " + failure.getMessage())
                    );
            protokollService.log(ProtokollKategorie.SECURITY, "Passwort-Reset angefordert", "Nutzer: " + email, nutzer.id);
        } else {
             protokollService.log(ProtokollKategorie.SECURITY, "Passwort-Reset für unbekannte E-Mail", "Email: " + email);
        }

        return Response.accepted().build();
    }

    @POST
    @Path("/reset-password")
    @PermitAll
    @Transactional
    public Response resetPassword(ResetRequest req) {
        Nutzer nutzer = Nutzer.find("resetToken", req.token).firstResult();
        if (nutzer != null && nutzer.resetTokenExpiry.isAfter(LocalDateTime.now())) {
            nutzer.passwordHash = BcryptUtil.bcryptHash(req.newPassword);
            nutzer.resetToken = null;
            protokollService.log(ProtokollKategorie.SECURITY, "Passwort erfolgreich zurückgesetzt", "Nutzer: " + nutzer.email, nutzer.id);
            return Response.ok().build();
        }
        protokollService.log(ProtokollKategorie.SECURITY, "Passwort-Reset fehlgeschlagen (Token ungültig/abgelaufen)");
        return Response.status(Response.Status.BAD_REQUEST).build();
    }

    @POST
    @Path("/login")
    @PermitAll
    @Transactional
    public Response login(LoginRequest loginRequest) {
        Nutzer nutzer = Nutzer.findByEmail(loginRequest.email);

        if (nutzer != null && BcryptUtil.matches(loginRequest.password, nutzer.passwordHash) && nutzer.isActive) {
            String token = Jwt.issuer("https://vortragsmanager.kreyj")
                    .upn(nutzer.email)
                    .groups(nutzer.role)
                    .expiresIn(Duration.ofHours(4))
                    .sign();
            protokollService.log(ProtokollKategorie.LOGIN, "Erfolgreicher Login", "Rolle: " + nutzer.role, nutzer.id);
            return Response.ok(new TokenResponse(token, nutzer.role)).build();
        }
        protokollService.log(ProtokollKategorie.SECURITY, "Fehlgeschlagener Login-Versuch", "E-Mail: " + loginRequest.email);
        return Response.status(Response.Status.UNAUTHORIZED).build();
    }
}
