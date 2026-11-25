// M2 can register an async provider that refreshes its Keycloak access token.
// Tokens stay in memory; this module does not store credentials in browser storage.
let tokenProvider = async () => null;

export function configureAccessTokenProvider(provider) {
  tokenProvider = typeof provider === 'function' ? provider : async () => null;
}

export async function authorizedFetch(url, options = {}) {
  const token = await tokenProvider();
  const headers = new Headers(options.headers || {});
  if (token) headers.set('Authorization', `Bearer ${token}`);
  return fetch(url, { ...options, headers });
}
