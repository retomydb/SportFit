# GitHub storage and Pages deployment

The source code and encrypted account files live in `retomydb/SportFit`. Keep this repository private. The UI can be published with GitHub Pages if the repository's GitHub plan permits Pages for private repositories. Publishing the site does not publish account files because the Pages artifact contains only `src/main/resources/static` assets.

The public UI and Java API have separate hosting. GitHub Pages cannot run Spring Boot or safely hold an owner token. The API is deployed to the user's existing backend hosting; its public HTTPS origin is the repository variable `API_BASE_URL`.

## API settings

Build using the included Dockerfile or Java 21 with `mvn package`. Run `java -jar target/sportfit-pro-1.0.0.jar`. Set these server environment variables:

| Variable | Value |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `github` |
| `PORT` | Hosting provider's assigned port |
| `SPORTFIT_GITHUB_REPOSITORY` | `retomydb/SportFit` |
| `SPORTFIT_GITHUB_BRANCH` | `main` |
| `SPORTFIT_GITHUB_TOKEN` | Server-only token authorized for repository Contents read/write |
| `SPORTFIT_DATA_KEY` | Existing base64 32-byte AES key generated for this project |
| `SPORTFIT_ALLOWED_ORIGINS` | `https://retomydb.github.io` (comma-separated additional origins if needed) |

A local encryption key has been created in `.runtime/data-key` with restricted permissions. Transfer it through the hosting provider's secret settings and retain a private backup. It is ignored by Git. Never regenerate it for an existing database: older account files require the original key. The existing GitHub CLI credential can run the local server, but a repository-scoped server credential is preferable for ongoing hosting. Neither secret belongs in Actions variables or frontend config.

The GitHub profile uses GitHub as the source of truth for account records. H2 is in-memory metadata only in this profile. Server sessions use opaque `X-Auth-Token` headers; the UI keeps only the current session identifier in sessionStorage. This works across separate UI/API domains without third-party cookies. CORS permits only configured origins, and CSRF protection remains enabled. Sessions expire after eight idle hours and are lost on backend restart; use one backend instance with this in-memory session setup.

Health check: `GET /api/csrf`. API routes are unchanged. The service needs outbound HTTPS access to api.github.com and synchronized system time. GitHub data writes are synchronous; failed persistence returns an error to the UI, and SHA checks retry concurrent edits rather than silently overwriting them. GitHub API limits constrain this storage approach to small workloads.

## Local GitHub-backed server

After building, run `./scripts/start-github.sh`. It reads the existing GitHub CLI credential and the local data key into server environment variables without writing the token to this project.

To migrate previous local H2 accounts, stop the original server, keep a backup of the local H2 database, and start the GitHub-backed server once with:

```sh
./scripts/start-github.sh \
  '--spring.datasource.url=jdbc:h2:file:./data/sportfit;DB_CLOSE_ON_EXIT=FALSE' \
  --sportfit.migrate-local=true
```

It copies each existing account into encrypted GitHub storage and preserves any remote account already present. Subsequent ordinary starts use GitHub. Keep the H2 backup private until migration is verified.

## Pages

Enable GitHub Pages with **GitHub Actions** as source. Set the repository variable `API_BASE_URL` to the backend HTTPS origin, without a trailing slash. Run **Publish SportFit website**. The intended URL is `https://retomydb.github.io/SportFit/`.

The build uses relative asset paths for the `/SportFit/` project path. The account directory, Java source, local database, and secrets are never included in the published artifact. Changes beneath `data/` do not run the Pages workflow. Until `API_BASE_URL` is set, the deployed UI clearly reports that the account server still needs connecting and does not attempt GitHub token access from the browser.

To change the backend later, update `API_BASE_URL` and rerun the Pages workflow. Do not make this repository public merely to bypass a private-repository Pages plan limitation.

## Tests

`mvn test` covers the API and encrypted GitHub storage using a local fake Contents API. Storage tests verify encryption round-trips, duplicate accounts, conflict retries, and rejection of modified ciphertext/wrong keys. `scripts/check-ui.cjs` exercises the website against an isolated H2-backed test server. Real GitHub integration should use a clearly labelled synthetic account and confirm only encrypted ciphertext is committed.
