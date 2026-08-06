package kreyj.konfplan.adapter.in.web;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.mailer.MailTemplate;
import io.quarkus.qute.Location;
import io.smallrye.jwt.build.Jwt;
import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.security.PermitAll;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.adapter.in.web.dto.LoginRequest;
import kreyj.konfplan.adapter.in.web.dto.ResetRequest;
import kreyj.konfplan.adapter.in.web.dto.TokenResponse;
import kreyj.konfplan.domain.service.ForgotPasswordRateLimiterService;
import kreyj.konfplan.domain.service.LoginRateLimiterService;
import kreyj.konfplan.domain.service.ProtokollService;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.ProtokollKategorie;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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

    private final ProtokollService protokollService;

    private final LoginRateLimiterService loginRateLimiterService;

    private final ForgotPasswordRateLimiterService forgotPasswordRateLimiterService;

    public AuthResource(@Location("email/passwordReset") MailTemplate passwordResetTemplate,
                        ProtokollService protokollService, LoginRateLimiterService loginRateLimiterService,
                        ForgotPasswordRateLimiterService forgotPasswordRateLimiterService) {
        this.loginRateLimiterService = loginRateLimiterService;
        this.forgotPasswordRateLimiterService = forgotPasswordRateLimiterService;
        this.passwordResetTemplate = passwordResetTemplate;
        this.protokollService = protokollService;
    }

    @POST
    @Path("/forgot-password")
    @PermitAll
    @Transactional
    @Operation(summary = "Passwort vergessen", description = "Fordert eine E-Mail zum Zurücksetzen des Passworts an.")
    public Response forgotPassword(@Context HttpServerRequest request, @QueryParam("loginName") String loginName) {
        String ip = clientIp(request);

        if (forgotPasswordRateLimiterService.isBlocked(ip)) {
            long retryAfterSeconds = forgotPasswordRateLimiterService.remainingBlockDuration(ip).toSeconds();
            protokollService.log(ProtokollKategorie.SECURITY, "Passwort-Reset blockiert (zu viele Anfragen)", "IP: " + ip);
            return Response.status(429)
                    .header("Retry-After", retryAfterSeconds)
                    .build();
        }
        forgotPasswordRateLimiterService.recordAttempt(ip);

        Nutzer nutzer = Nutzer.findByLoginName(loginName);

        if (nutzer != null) {
            if (null == nutzer.getEmail()) {
                // Kein Self-Service-Reset möglich - nur ein Admin kann das Passwort zurücksetzen.
                protokollService.log(ProtokollKategorie.SECURITY, "Passwort-Reset ohne hinterlegte E-Mail angefordert", "Nutzer: " + loginName, nutzer.getId());
                return Response.accepted().build();
            }

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
            protokollService.log(ProtokollKategorie.SECURITY, "Passwort-Reset angefordert", "Nutzer: " + loginName, nutzer.getId());
        } else {
            protokollService.log(ProtokollKategorie.SECURITY, "Passwort-Reset für unbekannten Anmeldenamen", "LoginName: " + loginName);
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
    public Response login(@Context HttpServerRequest request,
                          @RequestBody(description = "Die Login-Anmeldeinformationen") LoginRequest loginRequest) {
        String ip = clientIp(request);

        if (loginRateLimiterService.isBlocked(ip)) {
            long retryAfterSeconds = loginRateLimiterService.remainingBlockDuration(ip).toSeconds();
            protokollService.log(ProtokollKategorie.SECURITY, "Login blockiert (zu viele Fehlversuche)", "IP: " + ip);
            return Response.status(429)
                    .header("Retry-After", retryAfterSeconds)
                    .build();
        }

        Nutzer nutzer = Nutzer.findByLoginName(loginRequest.loginName);

        if (nutzer != null && BcryptUtil.matches(loginRequest.password, nutzer.getPasswordHash())
                && nutzer.isActive()) {
            loginRateLimiterService.recordSuccess(ip);
            String token = Jwt.issuer("https://konfplan.kreyj")
                    .upn(nutzer.getLoginName())
                    .subject(nutzer.getLoginName())
                    .groups(nutzer.getRole())
                    .expiresIn(Duration.ofHours(4))
                    .sign();
            protokollService.log(ProtokollKategorie.LOGIN, "Erfolgreicher Login", "Rolle: " + nutzer.getRole(), nutzer.getId());
            return Response.ok(new TokenResponse(token, nutzer.getRole())).build();
        }
        loginRateLimiterService.recordFailure(ip);
        protokollService.log(ProtokollKategorie.SECURITY, "Fehlgeschlagener Login-Versuch", "LoginName: " + loginRequest.loginName);
        return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    /** Bevorzugt den ersten Eintrag in X-Forwarded-For (Reverse-Proxy-Betrieb), sonst die direkte Verbindungs-IP. */
    private static String clientIp(HttpServerRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.remoteAddress() != null ? request.remoteAddress().host() : "unknown";
    }
}
