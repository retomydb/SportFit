# SportFit Pro

A responsive fitness website built from `docs/MASTER-PROMPT.docx`. Runs in a web browser, using Java 21, Spring Boot, Spring Security, Maven, encrypted GitHub account storage (with H2 fallback), HTML, CSS, vanilla JavaScript and locally bundled Chart.js.

## GitHub storage and deployment

The GitHub-backed configuration stores encrypted account documents in the `retomydb/SportFit` repository. The Pages workflow publishes the UI separately; a hosted Java API handles authentication and secure repository writes. See [deployment instructions](docs/GITHUB-DEPLOYMENT.md) for environment variables, migration, secret handling and the backend connection. The Docker and HTTPS tunnel setup is documented in the deployment guide; start or reconnect it with `./scripts/start-docker.sh`.

## Open the website

Visit **http://localhost:8087/**. Create your own account on the sign-in page, then set your fitness profile. New accounts start with an empty activity history. The local server binds to 127.0.0.1.

```sh
cd /Users/oladimejishodipe/SportApp
./start.sh
```

The script detects Homebrew Java 21, builds the project, runs its backend tests, and launches the GitHub-backed server using the private local encryption key and existing GitHub CLI credential. Use `SPORTFIT_STORAGE=h2 ./start.sh` only for the local H2 fallback. Maven 3.9.11 and the downloaded dependencies are available in `.tools/` on this machine. On a different machine, install Java 21 and Maven 3.9+, then run `./start.sh`. Set `PORT` to use a different port. Stop the foreground server with Ctrl+C.

## Website areas

- **Overview:** actual activity totals, daily/weekly/monthly minutes chart, weekly goal, active-day markers, and upcoming planned sessions.
- **My profile:** registration, secure sign-in/out, profile editing, fitness level, goals, preferred activities, BMI calculation, and password changes. Passwords use BCrypt. Sessions use opaque header identifiers. State-changing requests use Spring Security CSRF tokens. Accounts have separate records.
- **Explore activities:** ten sports with generated photos, descriptions, equipment, intensity, difficulty, suggested duration and estimated calories. Filter by type, intensity, duration and name; save favourites or open an activity in the tracker.
- **Workout tracker:** start, pause, resume, finish and save; timer, target progress, repetition and set counters, a 60-second rest timer, estimated calories, and optional permission-based GPS distance for running/walking/cycling. Unfinished workouts are restored paused after refresh. Only total GPS distance is saved, not coordinates.
- **My progress:** stored workout history, minutes trend, session frequency, calorie estimates, sport distribution, weekly goal, current streak and previous-week comparison. Select daily, weekly or monthly periods; download a CSV activity report.
- **Workout planner:** seven-day calendar, add/edit/delete sessions, mark plans complete, and generate a weekly programme based on profile level, preferred activities, recorded activity mix and rest days. Generation preserves existing plans and rest days and adds up to one session per available day. Targets above seven may be filled manually with multiple daily sessions. Completing a plan does not fabricate a timed workout.
- **Health & wellness:** daily hydration, sleep, perceived intensity, manually entered optional heart rate, recovery self-assessment, rest days, history and recovery guidance. Health indicators are explicitly informational estimates.
- **Achievements:** eight badges calculated from stored workouts/check-ins, progress toward each badge, activity journal and CSV reports.

## Project layout

```text
SportApp/
├── pom.xml
├── start.sh
├── README.md
├── docs/MASTER-PROMPT.docx
├── scripts/
│   ├── generate-images.mjs
│   └── check-ui.cjs
├── src/main/java/com/sportfit/
│   ├── SportFitApplication.java
│   ├── SecurityConfig.java
│   ├── ApiController.java
│   └── Errors.java
├── src/main/resources/
│   ├── application.properties
│   ├── application-mysql.properties
│   ├── schema.sql
│   └── static/
│       ├── index.html
│       ├── app.js
│       ├── styles.css
│       ├── vendor/chart.umd.min.js
│       └── images/  (12 JPEGs and generation manifest)
├── src/test/java/com/sportfit/ApiTests.java
├── data/           (local persistent H2 database; private, ignored by git)
├── target/         (built executable JAR; ignored by git)
└── .tools/         (local build and test dependencies; ignored by git)
```

## Images

Twelve Grok-generated JPEGs are included: running, walking, cycling, swimming, football, basketball, yoga, strength, aerobics, stretching, trail-running hero, and recovery. The batch completed before the image-provider preference changed. Each image's prompt and model are recorded in `src/main/resources/static/images/manifest.json`.

No image generation is needed to run the website, and no API credential is stored in this project. To generate missing assets again, set `XAI_API_KEY` in your shell and run `node scripts/generate-images.mjs`. Existing files are preserved. The script also accepts a path to a local env file as its first argument; do not commit that file.

## Validation

```sh
# Backend integration tests (separate in-memory H2)
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./.tools/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=.tools/repository test

# Frontend DOM integration checks with real REST calls
npm install --prefix .tools/dom-tests jsdom@26.1.0
# In another terminal, start an isolated test server:
java -jar target/sportfit-pro-1.0.0.jar --server.port=8088 \
  '--spring.datasource.url=jdbc:h2:mem:ui;DB_CLOSE_DELAY=-1'
node scripts/check-ui.cjs
```

Backend integration tests cover authentication, password hashing, CSRF, validation, calorie computation, data storage, per-account ownership, plan updates/deletion, wellness upserts and password changes. The DOM integration suite covers account creation/sign-in, profile/BMI, saving/filtering sports, workout start/pause/resume/save, reps and sets, charts' data setup, CSV export, planner CRUD/completion/generation, hydration, recovery guidance, badges and sign-out. The DOM suite stubs canvas rendering and native dialog methods; it does not replace a browser layout or GPS-device test. Chrome automation was unavailable during this session, so desktop/mobile visual inspection and real-device GPS accuracy remain unverified.

## MySQL option

Create a database and a dedicated database user. Set `DB_URL`, `DB_USER`, `DB_PASSWORD`, then run the JAR with `--spring.profiles.active=mysql`. The JDBC driver and compatible schema are included. This profile is supplied but has not been tested against a running MySQL instance. Existing H2 records require a separate data migration.

## Specification note

The supplied Word document ends mid-sentence after “Display exercise” in Dashboard 7. Achievement badges, motivation, exercise history and downloadable reports implement the available requirements. No missing source text was assumed to exist.
