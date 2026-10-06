[中文](README.md) | [English](README.en.md)

# ConfigPair · Pairwise Configuration Testing and Coverage Review

![ZhiHua Technology logo](frontend/public/brand/logo.jpg)

**ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)** · [Official website](https://www.zhuatech.cn/).

**Public source for learning / non-commercial use · 0.1.0.** The project's own code is governed by the [ZhuaTech Non-Commercial Source License 1.0](LICENSE). Use is limited to personal learning, technical research and non-commercial exchange. Commercial use requires prior written authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd. Publicly readable source is not an OSI open-source license. Third-party copyrights and licenses remain separate.

## Introduction and use cases

Discrete choices such as browser, authentication method, interface language and device can create a large compatibility test space. ConfigPair is a Java 21 / Spring Boot / Vue 3 / MySQL application for studying test design, development verification and independent quality review. Teams define candidate values and forbidden pairs, generate a deterministic plan covering every feasible pair, record actual manual results and independently review and seal the report.

Planned coverage, executed coverage and passing coverage are calculated separately. A plan with 100% pairwise coverage has not necessarily been executed and does not prove that the system has no defects. Failed cases count toward execution; blocked and unrecorded cases do not. Both the interface and exported reports preserve these distinctions.

Detailed documentation is currently in Chinese: [User manual](docs/操作手册.md), [API reference](docs/接口说明.md), [Architecture and data](docs/架构与数据.md), and [Deployment and recovery](docs/部署与恢复.md).

### Workflow

```text
Define target, build, environment and acceptance criteria
  → Define discrete factors and forbidden pairs → Generate a complete pairwise plan
  → Submit for review → Independently approve and freeze
  → Open result registration → Assigned operator acknowledges
  → Manually record PASS / FAIL / BLOCKED for each case
  → Submit a completed or partially blocked report
  → Independently review and seal, or return for revision
```

Opening registration only enables software record keeping. The application does not execute the system under test, open evidence references or call external test tools. Designers, operators and reviewers are independent. Anyone who previously edited a plan cannot become its independent reviewer merely by changing roles.

## Implemented features

| Area | Implemented behavior |
|---|---|
| Test targets | Unique reference, responsible department, system type, name, build, environment, expected behavior, acceptance criteria and assigned reviewer/operator |
| Factor design | 2–8 factors with 2–8 explicit values each; unique codes and values, bounded inputs, editing and reference protection |
| Forbidden pairs | Up to 80 binary exclusions between different factors; reversed duplicate detection, required reasons and invalid-reference rejection |
| Generation | Complete enumeration of valid configurations; feasible pairs derived from those configurations; deterministic greedy selection covering every feasible pair |
| Coverage matrix | Switchable axes and planned/executed/passing views; explicit uncovered or infeasible pairs and separately listed unreachable values |
| Versions and review | Immutable input/plan JSON, algorithm version and SHA-256 digests; changed inputs invalidate the current plan while retaining historical snapshots; independent approval freezes the plan |
| Manual testing | Assigned operator acknowledgment; explicit PASS, FAIL or BLOCKED results, required explanations for failures/blocks, text evidence references and retained result history |
| Report sealing | Submission after all cases are registered; outcome consistency checks; independent sealing as all passed, defects present or partly blocked |
| Queries and exports | Authorized search, filters, sorting and pagination; JSON reports and UTF-8 BOM CSV with spreadsheet formula-injection protection |
| Administration | Session login/logout and password changes; accounts, roles, departments, navigation, permissions, system types, settings and audit records |
| Interface | Chinese/English, desktop/mobile layouts, actual empty states, explicit errors, official ZhiHua logo and a consultation entry |

### Business users and administrators

The business workspace supports plan designers, independent reviewers, assigned test operators and department readers. The administration area manages identities, roles, departments, menus, permissions, dictionaries and system settings. Both use the same application and remain subject to server-side authorization.

| Initial role | Scope and restrictions |
|---|---|
| Administrator | ALL-scope identity and catalog administration; business actions still require the assigned role, valid state and independence |
| Plan designer | Department plans, factors, exclusions, generation, submission, registration opening, cancellation and export |
| Independent reviewer | Department reading; review only assigned plans/reports that the reviewer has not edited or executed |
| Test operator | SELF scope; only assigned plans in the same department, acknowledgment, result registration and report submission |
| Department reader | Department reading, statistics and export; no business writes |

An operator-only role can access only assigned plans even if configured with ALL scope. SELF relationships include creation, historical editing or explicit assignment, and remain constrained by department. Account status, role, scope and permissions are checked on every request; revocation cannot be bypassed through a cached successful response. Transactional writes check the whole-plan version and a UUID request key. Identical retries replay the result; reuse for different content is rejected. Concurrent writes with an old version cannot both succeed.

## Algorithm and limits

1. **The raw Cartesian-product limit is checked before exclusions.** The product of all factor value counts must not exceed 20,000. Exclusions cannot bypass this bound.
2. **Every valid configuration is enumerated.** Zero valid configurations produces an explicit error. The generator does not sample, truncate or silently omit configurations.
3. **Only feasible pairs enter the denominator.** A value may be completely unreachable; such pairs are displayed as infeasible rather than missing tests.
4. **Deterministic greedy selection.** `FEASIBLE-PAIR-GREEDY-1` repeatedly selects the valid configuration covering the largest number of new pairs, with stable enumeration order breaking ties. Plans contain at most 256 cases; exceeding the bound fails explicitly. Complete feasible-pair coverage is guaranteed for a returned plan, but minimum case count is not.
5. **Actual coverage comes from manual results.** PASS/FAIL contribute to executed coverage; only PASS contributes to passing coverage. BLOCKED/PENDING contribute to neither. Pairs are deduplicated across cases.
6. **The sealed outcome follows all recorded facts.** No blocks and all passes yields `PASSED`; failures without blocks yield `ISSUES`; any block yields `PARTIAL`. Unrecorded cases prevent submission, and a completion declaration cannot hide blocks.

The [NIST combinatorial testing guide](https://csrc.nist.gov/pubs/sp/800/142/final) and [coverage measurement reference](https://www.nist.gov/publications/combinatorial-coverage-measurement) explain the underlying concepts. ConfigPair implements its own bounded binary model; it neither integrates nor copies ACTS. Pairwise coverage cannot guarantee detection of faults involving three or more factors. Actual testing still requires domain-specific criteria and risk assessment.

Each plan permits at most 50 generations. The `maxRecords` setting limits total plans to 100–1,000; successful request keys are capped at 10,000. Stored inputs, approval digests and results are traceable, but a digest cannot prevent a privileged database administrator from modifying both data and digests.

## Actual running pages

These screenshots show isolated `TEST` acceptance records in the running application. A normal first start has an empty business database. They are not fabricated product mockups.

| Business workspace | Statistics and administration |
|---|---|
| ![Login](docs/screenshots/login.jpg)<br>**Login:** enter the workspace through session authentication. | ![Plan list](docs/screenshots/jobs.jpg)<br>**Plan list:** search and filter authorized plans. |
| ![Factors and exclusions](docs/screenshots/model.jpg)<br>**Model:** maintain discrete values and forbidden pairs. | ![Coverage dashboard](docs/screenshots/dashboard.jpg)<br>**Dashboard:** separate planned, executed and passing coverage. |
| ![Coverage matrix](docs/screenshots/matrix.jpg)<br>**Matrix:** switch axes and distinguish infeasible pairs. | ![Account administration](docs/screenshots/users.jpg)<br>**Accounts:** maintain departments, roles and enabled status. |
| ![Actual case results](docs/screenshots/cases.jpg)<br>**Results:** assigned operators register PASS, FAIL or BLOCKED. | ![Roles and permissions](docs/screenshots/roles.jpg)<br>**Roles:** configure permissions and data scopes. |
| ![Version and result history](docs/screenshots/history.jpg)<br>**History:** inspect generation snapshots and result revisions. | ![System settings](docs/screenshots/settings.jpg)<br>**Settings:** maintain supported workspace settings. |
| ![English interface](docs/screenshots/english.jpg)<br>**English UI:** view English operation pages. | ![Mobile interface](docs/screenshots/mobile.jpg)<br>**Mobile UI:** use plans in the narrow-screen layout. |

## Architecture and repository layout

| Component | Technology/version |
|---|---|
| Backend | Java 21, Maven 3.9, Spring Boot 4.0.7, Security, JPA, Flyway, MariaDB JDBC 3.5.10 |
| Frontend | Node 24.19.0, npm 11, Vue 3.5.40, Vite 8.1.5, Lucide 1.48.0, ESLint/Prettier |
| Database | MySQL 8.4 (MySQL 8 family); 17 identity/business tables plus Flyway history, 18 tables total |
| Deployment | Compose services for MySQL, Java and Nginx; database/backend stay on the project network; frontend defaults to loopback port 8133 |
| Time | UTC microsecond persistence; interface defaults to Asia/Shanghai |

```text
backend/src/main/java/cn/zhuatech/configpair/ Identity, algorithm, transactions and API
backend/src/main/resources/db/migration/    V1 identity / V2 coverage and results
backend/src/test/                           HTTP/JPA and independent algorithm tests
frontend/src/                              Pages, forms, API, domain functions and tests
frontend/public/brand/                     Original brand assets
docs/                                     Manuals, API, architecture, deployment, screenshots, licenses
scripts/                                  Random local configuration, isolated acceptance, release checks
compose.yaml                              Complete local application
```

### Database initialization

Versioned migrations are in `backend/src/main/resources/db/migration/`: `V1__identity.sql` and `V2__pair_coverage.sql`. Flyway creates the schema; Hibernate only validates it. Composite foreign keys keep excluded factors and result revisions within their parent plan. Unique constraints protect references, factor codes, case results and request keys. Transactional checks enforce states, independence, coverage and digests.

An empty database initializes headquarters, five roles, eight permissions, ten menus, four system types, three settings and `admin`. It creates no business plans or actual results.

## Requirements, installation and startup

The container route requires Docker, Docker Compose 2 and Python 3.11+. Builds require access to official images, Maven Central and npm. The backend image build runs all backend tests.

```bash
python3 scripts/init-env.py
docker compose config --quiet
docker compose up --build -d --wait
```

Open [http://127.0.0.1:8133/](http://127.0.0.1:8133/). The [health endpoint](http://127.0.0.1:8133/actuator/health) returns `"status":"UP"`. The initial username is `admin`; read `ADMIN_PASSWORD` from the local `.env`. The initialization script generates three independent random passwords, writes the file with mode 0600 and refuses to overwrite it. `.env` is ignored by Git. Changing an environment variable does not reset an existing database account.

Create actual departments and separate designer, reviewer and operator accounts in the same responsible department before creating plans. `docker compose down` preserves the database volume. Delete volumes only for explicitly disposable test databases.

### Configuration

| Variable | Meaning |
|---|---|
| `DATABASE_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `ADMIN_PASSWORD` | Required; generated and stored locally only |
| `WEB_PORT`, `BIND_ADDRESS` | Defaults: 8133 and 127.0.0.1; override `WEB_PORT` when occupied |
| `COOKIE_SECURE` | `false` for local HTTP; `true` behind trusted external HTTPS |
| `DATABASE_URL`, `DATABASE_USER`, `DATABASE_CATALOG` | Backend supports external connections; inject through `backend.environment` in a deployment override. Merely adding these names to `.env` does not inject them into the default Compose service |

### Source development

Provide Java 21, Maven 3.9 and a local MySQL database, and set the backend database configuration and `ADMIN_PASSWORD`:

```bash
cd backend
mvn spring-boot:run
```

In another terminal, use Node 24.19.0 / npm 11:

```bash
cd frontend
npm ci
npm run dev
```

Vite proxies `/api` and `/actuator` to local port 8080. Without host Java/Maven, use the official `maven:3.9-eclipse-temurin-21` image. Deployment overrides, upgrades and independent restores are described in the [deployment manual](docs/部署与恢复.md).

## Testing, upgrades and troubleshooting

```bash
cd backend
export TEST_ADMIN_PASSWORD="$(python3 -c 'import secrets; print("Aa9" + secrets.token_urlsafe(24))')"
mvn spotless:check test package
unset TEST_ADMIN_PASSWORD
cd ../frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
cd ..
docker compose config --quiet
python3 scripts/release-check.py
git diff --check
```

The backend has 43 tests: 26 HTTP/JPA tests for business behavior, authorization, revocation, idempotency, concurrency, independence and history, plus 17 algorithm tests, including independent exhaustive checks for 80 fixed-seed models and the 20,000-configuration bound. The frontend has 15 tests for value parsing, feasible-pair indexing, coverage definitions, role/state behavior, API handling and CSRF. The isolated MySQL acceptance suite covers seven plans, pass/fail/block outcomes, returns, cancellation, history, revocation, concurrency, CSV and independently recomputed coverage, with 1,174 assertions.

`TEST_ADMIN_PASSWORD` is a temporary random password for backend tests only, not the running application's administrator password. Do not put it in source files or reuse actual business-account credentials.

```bash
# Only for an explicitly disposable, fresh, isolated loopback instance.
# This creates TEST accounts and business records.
python3 scripts/smoke.py --allow-test-writes
python3 scripts/smoke.py --capture
# After restarting, or restoring a restricted backup into an independent database:
python3 scripts/smoke.py --verify
# Independent restores may use TEST_URL=http://127.0.0.1:18133
```

Private `output/qa-state.json` contains random test passwords and comparison snapshots; it has mode 0600 and is ignored. Do not publish logs or backups. Before an upgrade, back up and verify new migrations in an independent test database. Add a new Flyway version; do not edit published SQL, disable validation or automatically repair history.

| Problem | Action |
|---|---|
| Raw configuration limit exceeded | Reduce candidate values or split the model; exclusions do not lower the raw bound |
| No valid configuration | Check whether exclusions eliminate all possibilities; do not ignore them |
| Unreachable candidate value | Review the model/exclusions; unreachable pairs stay outside the denominator |
| Current plan missing after edits | Generate again; older snapshots remain available |
| No selectable reviewer/operator | Use enabled same-department accounts with the required permissions, independent of the current designer |
| Report submission rejected | Register every result; explain failures/blocks and declare an outcome consistent with blocks |
| Version conflict | Refresh the plan before reopening the form |
| Changed initial password has no effect | `ADMIN_PASSWORD` only initializes an empty database; use the authorized password-change flow for existing accounts |
| Startup/migration failure | Inspect restricted local logs and fix the cause; do not skip tests or clear a business database |

## Deployment and security

The Compose deployment keeps MySQL and the backend off host ports and exposes only the frontend. Production-facing deployment additionally requires trusted HTTPS, filtered proxy forwarding headers, restricted database accounts, network access controls and tested backups/restores. These requirements are deployment responsibilities; this learning release is not a claim of independently verified production readiness.

Security controls include BCrypt with 12 rounds, HttpOnly/SameSite Strict session cookies, CSRF protection, login rate limiting, live authorization, data scopes, bounded DTOs, transaction locks, version/digest checks, parameterized database operations and CSV formula-injection protection. Do not publish customer configurations, actual passwords, personal data or unredacted evidence references.

## Known limitations and external dependencies

This release does not implement three-way or higher-strength coverage, arbitrary Boolean constraints, continuous intervals, an exact minimum-case solver, test-script generation/execution, CI release gating, defect tickets, external device/account integrations, multi-tenancy, high availability or external tamper-proof signatures.

It has no preloaded business examples, fabricated results or third-party-service demo mode; core business data is actually persisted. No AI credentials or external testing service are required. External HTTPS and backup storage must be configured by the deployer. Evidence references are text only: no automatic browsing, attachment upload or evidence-authenticity verification. Pairwise coverage, manual records and consistency digests do not replace actual tests, domain risk assessment or release approval, and cannot guarantee detection of every defect.

## Contributions, license and contact

See [CONTRIBUTING](CONTRIBUTING.md) for contributions. Use redacted issues for general questions. For security disclosures, consult [SECURITY](SECURITY.md) or contact ZhiHua privately through the channels below. [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES.md) retains third-party notices. The root [LICENSE](LICENSE) governs the project's own code; commercial use is prohibited without prior written authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd.

For commercial licensing, private deployment, system integration or in-depth custom development, contact **ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)**:

- Website: [https://www.zhuatech.cn/](https://www.zhuatech.cn/)
- Email: [han@zhuatech.cn](mailto:han@zhuatech.cn)
- Email: [jack@zhuatech.cn](mailto:jack@zhuatech.cn)
- WhatsApp: [+86 17521234993](https://wa.me/8617521234993)
