# Encrypted account data

The SportFit server stores one encrypted account document per user under `data/accounts/<opaque-id>.json.enc` on the `main` branch of this repository. Each document contains an AES-256-GCM encrypted profile, BCrypt password hash, workout history, plans, and wellness records.

Only the backend can decrypt these files. The server-side `SPORTFIT_DATA_KEY` and `SPORTFIT_GITHUB_TOKEN` are never committed. Filenames are SHA-256 digests of normalized email addresses; they contain no plaintext email, but should not be treated as anonymization. This repository is currently public to support Pages, so the encrypted envelopes and commit history are publicly readable. Keep the server key secret. For production privacy, a private storage repository can be selected with `SPORTFIT_GITHUB_REPOSITORY`.

The GitHub Pages workflow publishes only the static website files. This directory is excluded from the Pages artifact, and data writes do not trigger website deployment.

Local H2 files are ignored by Git and retained only as a local fallback/migration source. Do not commit them. Git history preserves older encrypted revisions; deleting a current file does not erase its history.
