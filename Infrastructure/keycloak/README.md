# Import the local `ai-lab` realm

1. Start Keycloak from `Infrastructure/docker-compose.yml` (or use an existing local Keycloak).
2. In the administration console, select **Create realm**, choose **Browse**, and import `realm-ai-lab.json`. When using the compose service, start with `--import-realm`; its mounted realm file is imported automatically.
3. Use realm `ai-lab`. Sample users `alice` and `root` have temporary passwords `alice-temp-change-me` and `root-temp-change-me`; Keycloak requires changing them at first login. Alice has role `user`; root has `admin` and `user`.
4. Configure M1's `KEYCLOAK_ISSUER_URI` to the issuer URL that appears in tokens. For local host access the default is `http://localhost:8180/realms/ai-lab`.

The compose bootstrap administrator is `admin` / `admin-change-me`; it is a local-only bootstrap value and must be replaced for any persistent or shared deployment.

For a local command-line smoke test only, first sign in once and change the temporary password, then exchange the new password stored in an environment variable:

```sh
curl -X POST http://localhost:8180/realms/ai-lab/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'client_id=ai-lab-frontend' \
  --data-urlencode 'grant_type=password' \
  --data-urlencode 'username=alice' \
  --data-urlencode "password=$ALICE_PASSWORD"
```

The frontend client is public, uses standard authorization code flow with PKCE S256, and has no client secret. Do not use the sample credentials outside a local demonstration; replace them immediately.
