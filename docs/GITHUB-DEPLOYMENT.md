# GitHub storage and Pages deployment

The source code and encrypted account files live in `retomydb/SportFit`. The repository was made public by the owner to enable GitHub Pages on the current plan. Account files remain encrypted, but their ciphertext and history are public. For production privacy, a private storage repository can be selected independently with `SPORTFIT_GITHUB_REPOSITORY`. Publishing the site does not publish account files because the Pages artifact contains only `src/main/resources/static` assets.

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

The build uses relative asset paths for the `/SportFit/` project path, embeds the current public API setting directly in the HTML, and uses content-versioned JS/CSS filenames so a cached empty config.js cannot disable login. The account directory, Java source, local database, and secrets are never included in the published artifact. Changes beneath `data/` do not run the Pages workflow. Until `API_BASE_URL` is set, the deployed UI clearly reports that the account server still needs connecting and does not attempt GitHub token access from the browser.

To change the backend later, update `API_BASE_URL` and rerun the Pages workflow. If the repository is made private again, the GitHub plan must support Pages for private repositories.

## Tests

`mvn test` covers the API and encrypted GitHub storage using a local fake Contents API. Storage tests verify encryption round-trips, duplicate accounts, conflict retries, and rejection of modified ciphertext/wrong keys. `scripts/check-ui.cjs` exercises the website against an isolated H2-backed test server. Real GitHub integration should use a clearly labelled synthetic account and confirm only encrypted ciphertext is committed.

## Docker on the owner's computer

`compose.yaml` runs the Java backend and Cloudflare tunnel on a separate Docker network. The backend's host port is bound only to `127.0.0.1:8087`; remote visitors reach it through HTTPS. The GitHub token and existing encryption key are mounted as Docker secrets from ignored local files in `.runtime/`, rather than being copied into the image or published to Pages.

Start or reconnect:

```sh
cd /Users/oladimejishodipe/SportApp
./scripts/start-docker.sh
```

This starts Docker Desktop if needed, builds and starts the two containers, checks the public API, updates the repository's `API_BASE_URL` variable and requests a new Pages deployment. Keep the computer awake, online, and running Docker. A local non-Docker server on port 8087 must be stopped before starting the container.

The included Quick Tunnel does not require a Cloudflare account or router changes. Its hostname is temporary and may change when the tunnel is recreated. After a Docker/tunnel restart, run the reconnect command above so Pages receives the current address. If only the Pages connection needs refreshing, run `node scripts/connect-pages.mjs`. For a permanent address and production use, configure a named Cloudflare Tunnel with your own account/domain: https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/.

Check or stop only SportFit:

```sh
docker compose ps
docker compose logs --tail=50 backend tunnel
docker compose stop
```

Other Docker projects are independent. `scripts/check-public-api.mjs` optionally verifies real HTTPS registration, session authentication and GitHub persistence using a synthetic account, then removes that account's current encrypted file. Its synthetic revisions remain in Git history.
