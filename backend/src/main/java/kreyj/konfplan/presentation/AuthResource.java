package kreyj.konfplan.presentation;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.mailer.MailTemplate;
import io.quarkus.qute.Location;
import io.smallrye.jwt.build.Jwt;
import jakarta.annotation.security.PermitAll;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.application.service.ProtokollService;
import kreyj.konfplan.presentation.dto.LoginRequest;
import kreyj.konfplan.presentation.dto.ResetRequest;
import kreyj.konfplan.presentation.dto.TokenResponse;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.ProtokollKategorie;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Path("/api/auth")
@Tag(name = "Authentifizierung", description = "Endpunkte für Login und Passwort-Reset")
public class AuthResource {
    @ConfigProperty(name = "app.mail.admin", defaultValue = "konfplan@yahoo.com")
    String adminEmail;

    private final MailTemplate passwordResetTemplate;

    private final JsonWebToken jwt;

    private final ProtokollService protokollService;

    public AuthResource(@Location("email/passwordReset") MailTemplate passwordResetTemplate,
                        JsonWebToken jwt,
                        ProtokollService protokollService) {
        this.passwordResetTemplate = passwordResetTemplate;
        this.jwt = jwt;
        this.protokollService = protokollService;
    }

    @POST
    @Path("/forgot-password")
    @PermitAll
    @Transactional
    @Operation(summary = "Passwort vergessen", description = "Fordert eine E-Mail zum Zurücksetzen des Passworts an.")
    public Response forgotPassword(@QueryParam("email") String email) {
        Nutzer nutzer = Nutzer.findByEmail(email);

        if (nutzer != null) {
            String token = UUID.randomUUID().toString();
            nutzer.setResetToken(token);
            nutzer.setResetTokenExpiry(LocalDateTime.now().plusHours(2));
            nutzer.persist();

            String resetUrl = "http://localhost:5173/reset-password?token=" + token;

            passwordResetTemplate.to(nutzer.getEmail())
                    .from(adminEmail)
                    .subject("Passwort zurücksetzen - KonfPlan")
                    .data("firstName", nutzer.getFirstName())
                    .data("resetLink", resetUrl)
                    .send()
                    .subscribe().with(
                            success -> System.out.println("Mail gesendet an " + nutzer.getEmail()),
                            failure -> System.err.println("Mail-Fehler: " + failure.getMessage())
                    );
            protokollService.log(ProtokollKategorie.SECURITY, "Passwort-Reset angefordert", "Nutzer: " + email, nutzer.getId());
        } else {
            protokollService.log(ProtokollKategorie.SECURITY, "Passwort-Reset für unbekannte E-Mail", "Email: " + email);
        }

        return Response.accepted().build();
    }

    @POST
    @Path("/reset-password")
    @PermitAll
    @Transactional
    @Operation(summary = "Passwort zurücksetzen", description = "Setzt das Passwort mit einem gültigen Token zurück.")
    public Response resetPassword(@RequestBody(description = "Das Reset-Anfrage-Objekt") ResetRequest req) {
        Nutzer nutzer = Nutzer.find("resetToken", req.token).firstResult();
        if (nutzer != null && nutzer.getResetTokenExpiry().isAfter(LocalDateTime.now())) {
            nutzer.setPasswordHash(BcryptUtil.bcryptHash(req.newPassword));
            nutzer.setResetToken(null);
            protokollService.log(ProtokollKategorie.SECURITY, "Passwort erfolgreich zurückgesetzt", "Nutzer: " + nutzer.getEmail(), nutzer.getId());
            return Response.ok().build();
        }
        protokollService.log(ProtokollKategorie.SECURITY, "Passwort-Reset fehlgeschlagen (Token ungültig/abgelaufen)");
        return Response.status(Response.Status.BAD_REQUEST).build();
    }

    @POST
    @Path("/login")
    @PermitAll
    @Transactional
    @Operation(summary = "Login", description = "Authentifiziert einen Nutzer und gibt einen JWT-Token zurück.")
    public Response login(@RequestBody(description = "Die Login-Anmeldeinformationen") LoginRequest loginRequest) {
        Nutzer nutzer = Nutzer.findByEmail(loginRequest.email);

        if (nutzer != null && BcryptUtil.matches(loginRequest.password, nutzer.getPasswordHash())
                && nutzer.isActive()) {
            String token = Jwt.issuer("https://konfplan.kreyj")
                    .upn(nutzer.getEmail())
                    .subject(nutzer.getEmail())
                    .groups(nutzer.getRole())
                    .expiresIn(Duration.ofHours(4))
                    .sign();
            protokollService.log(ProtokollKategorie.LOGIN, "Erfolgreicher Login", "Rolle: " + nutzer.getRole(), nutzer.getId());
            return Response.ok(new TokenResponse(token, nutzer.getRole())).build();
        }
        protokollService.log(ProtokollKategorie.SECURITY, "Fehlgeschlagener Login-Versuch", "E-Mail: " + loginRequest.email);
        return Response.status(Response.Status.UNAUTHORIZED).build();
    }
}