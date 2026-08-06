package kreyj.konfplan.adapter.in.web;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import kreyj.konfplan.domain.service.TokenInvalidationService;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.Instant;

/**
 * Lehnt bei jeder authentifizierten Anfrage Tokens ab, die vor einem seither erfolgten
 * Passwort-Reset ausgestellt wurden (siehe TokenInvalidationService) - ohne diese Pruefung wuerde
 * die JWT-Signaturvalidierung ein solches Token bis zu seinem Ablauf (4h) weiterhin akzeptieren.
 */
@ApplicationScoped
public class TokenInvalidationAugmentor implements SecurityIdentityAugmentor {

    @Inject
    TokenInvalidationService tokenInvalidationService;

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (identity.isAnonymous()) {
            return Uni.createFrom().item(identity);
        }

        JsonWebToken jwt = identity.getPrincipal(JsonWebToken.class);
        if (jwt != null) {
            Instant issuedAt = Instant.ofEpochSecond(jwt.getIssuedAtTime());
            if (!tokenInvalidationService.isValid(jwt.getName(), issuedAt)) {
                return Uni.createFrom().failure(
                        new AuthenticationFailedException("Token wurde durch einen Passwort-Reset ungültig."));
            }
        }

        return Uni.createFrom().item(identity);
    }
}
