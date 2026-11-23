package de.cronn.keycloak.provisioning.connection.client;

import de.cronn.keycloak.provisioning.KeycloakProvidedClientScope;
import de.cronn.keycloak.provisioning.RealmManager;
import java.util.List;
import java.util.Map;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates the client this library authenticates as. {@link
 * de.cronn.keycloak.provisioning.connection.DefaultKeycloakConnectionManager} calls this on every
 * connect, so a first start needs nothing prepared in the admin console; call it yourself only when
 * managing the {@code Keycloak} connection without that manager.
 */
public final class ProvisioningClientBootstrapper {

  private static final Logger log = LoggerFactory.getLogger(ProvisioningClientBootstrapper.class);

  private static final String DPOP_BOUND_ACCESS_TOKENS_ATTRIBUTE = "dpop.bound.access.tokens";

  private ProvisioningClientBootstrapper() {}

  /**
   * Creates (or updates) a confidential client with a service account in the master realm and
   * grants that service account the realm's {@code admin} role so it can be used to authenticate
   * future provisioning runs.
   */
  public static void registerClient(
      RealmManager realmManager, KeycloakProvisioningClientConfig provisioningClientConfig) {
    ClientRepresentation client =
        getProvisioningClientRepresentation(
            provisioningClientConfig, getDefaultClientScopes(realmManager));
    realmManager.createOrUpdateUnmanagedClient(client);
    RoleRepresentation adminRole =
        realmManager
            .getRealmResource()
            .roles()
            .get(RealmManager.ADMIN_REALM_ROLE)
            .toRepresentation();
    RoleScopeResource serviceAccountRoles =
        realmManager.getServiceAccountRealmRoleScope(client.getClientId());
    boolean hasAdminRole =
        serviceAccountRoles.listAll().stream()
            .anyMatch(role -> role.getName().equals(adminRole.getName()));
    if (hasAdminRole) {
      return;
    }
    serviceAccountRoles.add(List.of(adminRole));

    log.info("Added admin role to service account for client '{}'", client.getClientId());
  }

  /**
   * Keycloak 26.8.2 and later reject Admin API tokens that lack the {@code kc-admin-api} scope.
   * Older servers lack this scope, so this method returns it only where the realm has it.
   */
  private static List<String> getDefaultClientScopes(RealmManager realmManager) {
    String adminApiScope = KeycloakProvidedClientScope.KC_ADMIN_API.getScopeName();
    boolean realmHasAdminApiScope =
        realmManager.getRealmResource().clientScopes().findAll().stream()
            .anyMatch(scope -> scope.getName().equals(adminApiScope));
    return realmHasAdminApiScope ? List.of(adminApiScope) : List.of();
  }

  private static ClientRepresentation getProvisioningClientRepresentation(
      KeycloakProvisioningClientConfig provisioningClientConfig, List<String> defaultClientScopes) {
    ClientRepresentation client = new ClientRepresentation();
    client.setClientId(provisioningClientConfig.clientId());
    client.setSecret(provisioningClientConfig.secret());
    client.setAuthorizationServicesEnabled(false);
    client.setServiceAccountsEnabled(true);
    client.setPublicClient(false);
    client.setStandardFlowEnabled(false);
    client.setDirectAccessGrantsEnabled(false);
    // The scope lists below grant no roles, so without full scope the service account's admin
    // role never reaches the token and every call answers 403.
    client.setFullScopeAllowed(true);
    client.setEnabled(true);
    client.setName(provisioningClientConfig.name());
    client.setDescription(provisioningClientConfig.description());
    // A service account that never issues user tokens has no use for the realm's default scopes.
    client.setDefaultClientScopes(defaultClientScopes);
    client.setOptionalClientScopes(List.of());
    if (provisioningClientConfig.useDPoP()) {
      client.setAttributes(Map.of(DPOP_BOUND_ACCESS_TOKENS_ATTRIBUTE, "true"));
    }

    return client;
  }
}
