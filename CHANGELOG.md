# Changelog

All notable changes to this library are documented in this file. The format is based on
[Keep a Changelog][keep-a-changelog]. Releases before 0.3.0 are not covered.

## [Unreleased]

### Changed

- **Keycloak 26.8.2 compatibility.** The provisioning client gets the `kc-admin-api` client scope as a default scope, because
  Keycloak 26.8.2 and later accept only Admin API tokens with that scope
  ([upgrading guide][keycloak-admin-audience]). That means Keycloak 26.8.2 and later is only compatible with
  keycloak-provisioning 0.3.0 and onward. The other way around keycloak-provisioning 0.3.0 keeps working with
  older keycloak versions though, since the library assigns the scope on connect, and only where the master realm has it.

  **⚠️ Upgrading to Keycloak 26.8.2:** The provisioning client registered in earlier Keycloak versions lacks
  the scope. Against Keycloak 26.8.2, the client can only connect through the fallback, which
  authenticates through `admin-cli` and the bootstrap admin. If you disabled `admin-cli`, as the README
  suggests for hardened setups, upgrade the library first. Then build and deploy Keycloak once with
  `--spi-admin--audience-check-enabled=false` (as an environment variable,
  `KC_SPI_ADMIN__AUDIENCE_CHECK_ENABLED=false`), run provisioning, then rebuild without the option.
  Keycloak reads this option at build time, so passing it to `start` works only without
  `--optimized`. Alternatively once running 26.8.2, add `kc-admin-api` to the provisioning client's
  default client scopes in the admin console or assign it directly in the database:

  ```sql
    INSERT INTO client_scope_client (client_id, scope_id, default_scope)
    SELECT c.id, s.id, true
    FROM client c
    JOIN realm r ON r.id = c.realm_id
    JOIN client_scope s ON s.realm_id = r.id
    WHERE r.name = 'master'
    AND c.client_id = '<PROVISIONING-CLIENT-NAME>'
    AND s.name = 'kc-admin-api'
    ON CONFLICT DO NOTHING;
  ```

[Unreleased]: https://github.com/cronn/keycloak-provisioning/compare/v0.2.0...HEAD
[keep-a-changelog]: https://keepachangelog.com/en/1.1.0/
[keycloak-admin-audience]: https://www.keycloak.org/docs/latest/upgrading/#admin-rest-api-now-enforces-an-audience-check
