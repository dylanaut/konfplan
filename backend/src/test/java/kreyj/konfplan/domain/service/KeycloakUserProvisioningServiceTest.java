package kreyj.konfplan.domain.service;

import io.quarkus.runtime.LaunchMode;
import jakarta.ws.rs.core.Response;
import kreyj.konfplan.domain.exception.KeycloakProvisioningException;
import kreyj.konfplan.persistence.Organisator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycloakUserProvisioningServiceTest {

    private static final String REALM = "konfplan";

    private RealmResource realmResource;
    private UsersResource usersResource;
    private KeycloakUserProvisioningService service;


    @BeforeEach
    void setup() {
        Keycloak keycloak = mock(Keycloak.class);
        realmResource = mock(RealmResource.class);
        usersResource = mock(UsersResource.class);
        when(keycloak.realm(REALM)).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);

        service = new KeycloakUserProvisioningService(LaunchMode.TEST);
        service.keycloak = keycloak;
        service.realm = REALM;
    }


    private Organisator admin() {
        Organisator admin = new Organisator();
        admin.assignLoginName("kathrin.jessen");
        admin.setEmail("kathrin.jessen@rks-linz.de");
        admin.setFirstName("Kathrin");
        admin.setLastName("Jessen");
        return admin;
    }


    private void stubRoleAssignment(String keycloakId) {
        RolesResource rolesResource = mock(RolesResource.class);
        RoleResource roleResource = mock(RoleResource.class);
        when(realmResource.roles()).thenReturn(rolesResource);
        when(rolesResource.get("ORGANISATOR")).thenReturn(roleResource);
        RoleRepresentation role = new RoleRepresentation();
        role.setName("ORGANISATOR");
        when(roleResource.toRepresentation()).thenReturn(role);

        UserResource userResource = mock(UserResource.class);
        RoleMappingResource roleMappingResource = mock(RoleMappingResource.class);
        RoleScopeResource roleScopeResource = mock(RoleScopeResource.class);
        when(usersResource.get(keycloakId)).thenReturn(userResource);
        when(userResource.roles()).thenReturn(roleMappingResource);
        when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);
    }


    @Test
    void createUser_beiKonflikt_verknuepftMitBestehendemUserPerUsername() {
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(Response.status(409).build());

        UserRepresentation bestehender = new UserRepresentation();
        bestehender.setId("existing-id-123");
        when(usersResource.searchByUsername("kathrin.jessen", true)).thenReturn(List.of(bestehender));
        stubRoleAssignment("existing-id-123");

        Organisator admin = admin();
        service.createUser(admin);

        assertThat(admin.getKeycloakId()).isEqualTo("existing-id-123");
    }


    @Test
    void createUser_beiKonflikt_findetKeinenPerUsername_verknuepftPerEmail() {
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(Response.status(409).build());
        when(usersResource.searchByUsername("kathrin.jessen", true)).thenReturn(List.of());

        UserRepresentation bestehender = new UserRepresentation();
        bestehender.setId("existing-id-456");
        when(usersResource.searchByEmail("kathrin.jessen@rks-linz.de", true)).thenReturn(List.of(bestehender));
        stubRoleAssignment("existing-id-456");

        Organisator admin = admin();
        service.createUser(admin);

        assertThat(admin.getKeycloakId()).isEqualTo("existing-id-456");
    }


    @Test
    void createUser_beiKonfliktOhneTreffer_wirftException() {
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(Response.status(409).build());
        when(usersResource.searchByUsername("kathrin.jessen", true)).thenReturn(List.of());
        when(usersResource.searchByEmail("kathrin.jessen@rks-linz.de", true)).thenReturn(List.of());

        assertThatThrownBy(() -> service.createUser(admin()))
            .isInstanceOf(KeycloakProvisioningException.class)
            .hasMessageContaining("409");
    }


    @Test
    void createUser_beiErfolg_verwendetIdAusLocationHeader() {
        when(usersResource.create(any(UserRepresentation.class)))
            .thenReturn(Response.created(java.net.URI.create("http://keycloak/admin/realms/konfplan/users/new-id-789")).build());
        stubRoleAssignment("new-id-789");

        Organisator admin = admin();
        service.createUser(admin);

        assertThat(admin.getKeycloakId()).isEqualTo("new-id-789");
    }


    @Test
    void createUser_inDevTest_vergibtFestesPasswortZurTestbequemlichkeit() {
        when(usersResource.create(any(UserRepresentation.class)))
            .thenReturn(Response.created(java.net.URI.create("http://keycloak/admin/realms/konfplan/users/new-id-1")).build());
        stubRoleAssignment("new-id-1");

        service.createUser(admin());

        ArgumentCaptor<UserRepresentation> userCaptor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(usersResource).create(userCaptor.capture());
        List<CredentialRepresentation> credentials = userCaptor.getValue().getCredentials();
        assertThat(credentials).hasSize(1);
        assertThat(credentials.getFirst().getValue()).isEqualTo("Konfplan1!");
        assertThat(credentials.getFirst().isTemporary()).isFalse();
    }


    @Test
    void createUser_ausserhalbVonDevTest_vergibtKeinStartPasswort() {
        service = new KeycloakUserProvisioningService(LaunchMode.NORMAL);
        service.keycloak = mock(Keycloak.class);
        when(service.keycloak.realm(REALM)).thenReturn(realmResource);
        service.realm = REALM;

        when(usersResource.create(any(UserRepresentation.class)))
            .thenReturn(Response.created(java.net.URI.create("http://keycloak/admin/realms/konfplan/users/new-id-2")).build());
        stubRoleAssignment("new-id-2");

        service.createUser(admin());

        ArgumentCaptor<UserRepresentation> userCaptor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(usersResource).create(userCaptor.capture());
        assertThat(userCaptor.getValue().getCredentials()).isNull();
    }


    @Test
    void resetPassword_erzwingtPasswortAenderungBeimNaechstenLogin() {
        // Kein Dev/Test-Sonderfall mehr: der erzwungene Passwortwechsel ist der eigentliche Zweck
        // eines Organisator-Resets und muss deshalb auch in Dev/Test greifen (service aus @BeforeEach
        // laeuft bewusst mit LaunchMode.TEST).
        UserResource userResource = mock(UserResource.class);
        when(usersResource.get("kc-id-1")).thenReturn(userResource);
        UserRepresentation kcUser = new UserRepresentation();
        when(userResource.toRepresentation()).thenReturn(kcUser);

        Organisator admin = admin();
        admin.setKeycloakId("kc-id-1");
        service.resetPassword(admin, "neuesPasswort123");

        ArgumentCaptor<CredentialRepresentation> credCaptor = ArgumentCaptor.forClass(CredentialRepresentation.class);
        verify(userResource).resetPassword(credCaptor.capture());
        assertThat(credCaptor.getValue().isTemporary()).isTrue();

        assertThat(kcUser.getRequiredActions()).containsExactly("UPDATE_PASSWORD");
        verify(userResource).update(kcUser);
    }


    @Test
    void hatEchtesPasswort_ohneKeycloakId_liefertFalse() {
        Organisator admin = admin();
        assertThat(service.hatEchtesPasswort(admin)).isFalse();
    }


    @Test
    void hatEchtesPasswort_ausstehendeUpdatePasswordAction_liefertFalse() {
        UserResource userResource = mock(UserResource.class);
        when(usersResource.get("kc-id-1")).thenReturn(userResource);
        UserRepresentation kcUser = new UserRepresentation();
        kcUser.setRequiredActions(List.of("UPDATE_PASSWORD"));
        when(userResource.toRepresentation()).thenReturn(kcUser);

        Organisator admin = admin();
        admin.setKeycloakId("kc-id-1");

        assertThat(service.hatEchtesPasswort(admin)).isFalse();
    }


    @Test
    void hatEchtesPasswort_keineRequiredActionAberKeineCredentials_liefertFalse() {
        UserResource userResource = mock(UserResource.class);
        when(usersResource.get("kc-id-1")).thenReturn(userResource);
        UserRepresentation kcUser = new UserRepresentation();
        when(userResource.toRepresentation()).thenReturn(kcUser);
        when(userResource.credentials()).thenReturn(List.of());

        Organisator admin = admin();
        admin.setKeycloakId("kc-id-1");

        assertThat(service.hatEchtesPasswort(admin)).isFalse();
    }


    @Test
    void hatEchtesPasswort_keineRequiredActionUndCredentialVorhanden_liefertTrue() {
        UserResource userResource = mock(UserResource.class);
        when(usersResource.get("kc-id-1")).thenReturn(userResource);
        UserRepresentation kcUser = new UserRepresentation();
        when(userResource.toRepresentation()).thenReturn(kcUser);
        when(userResource.credentials()).thenReturn(List.of(new CredentialRepresentation()));

        Organisator admin = admin();
        admin.setKeycloakId("kc-id-1");

        assertThat(service.hatEchtesPasswort(admin)).isTrue();
    }
}
