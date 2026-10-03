export const environment = {
  production: false,
  // The gateway's own default is 8080, but on this machine something else
  // already owns that port, so local dev runs it on 8090 instead (see the
  // --server.port=8090 override used to start aegis-api-gateway).
  apiBaseUrl: 'http://localhost:8090',
  keycloakUrl: 'http://localhost:8088',
  keycloakRealm: 'aegis',
  keycloakClientId: 'aegis-admin-frontend'
};

