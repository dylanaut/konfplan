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
import kreyj.vortragsmanager.entity.User;
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

    @POST
    @Path("/forgot-password")
    @PermitAll
    @Transactional
    public Response forgotPassword(@QueryParam("email") String email) {
        User user = User.findByEmail(email);

        if (user != null) {
            String token = UUID.randomUUID().toString();
            user.resetToken = token;
            user.resetTokenExpiry = LocalDateTime.now().plusHours(2);
            user.persist();

            String resetUrl = "http://localhost:5173/reset-password?token=" + token;

            passwordResetTemplate.to(user.email)
                    .subject("Passwort zurücksetzen - Vortragsmanager")
                    .data("firstName", user.firstName)
                    .data("resetLink", resetUrl)
                    .send()
                    .subscribe().with(
                            success -> System.out.println("Mail gesendet an " + user.email),
                            failure -> System.err.println("Mail-Fehler: " + failure.getMessage())
                    );
        }

        return Response.accepted().build();
    }

    @POST
    @Path("/reset-password")
    @PermitAll
    @Transactional
    public Response resetPassword(ResetRequest req) {
        User user = User.find("resetToken", req.token).firstResult();
        if (user != null && user.resetTokenExpiry.isAfter(LocalDateTime.now())) {
            user.passwordHash = BcryptUtil.bcryptHash(req.newPassword);
            user.resetToken = null;
            return Response.ok().build();
        }
        return Response.status(Response.Status.BAD_REQUEST).build();
    }

    @POST
    @Path("/login")
    @PermitAll
    @Transactional
    public Response login(LoginRequest loginRequest) {
        User user = User.findByEmail(loginRequest.email);

        if (user != null && BcryptUtil.matches(loginRequest.password, user.passwordHash) && user.isActive) {
            // Wir lassen sign() ohne explizites Secret, 
            // damit Quarkus das Secret aus der application.properties (smallrye.jwt.sign.key) verwendet.
            String token = Jwt.issuer("https://vortragsmanager.kreyj")
                    .upn(user.email)
                    .groups(user.role)
                    .expiresIn(Duration.ofHours(8))
                    .sign();
            return Response.ok(new TokenResponse(token, user.role)).build();
        }
        return Response.status(Response.Status.UNAUTHORIZED).build();
    }
}
