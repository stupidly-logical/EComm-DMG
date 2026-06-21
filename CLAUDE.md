# CLAUDE.md

Guidance for working in this repository.

## What this is
A Spring Boot 3.3 / Java 21 e-commerce **order management system**: catalog → cart → checkout
→ fulfillment → returns, behind JWT RBAC. The defining concerns are **no oversell under
concurrency** and **atomic checkout**. See `README.md` for the full feature/API overview.

## Build, test, run
This machine's default `mvn`/`java` may not be 21 — always pin Java 21:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

./mvnw verify              # FULL suite: unit (*Test, Surefire) + integration (*IT, Failsafe)
./mvnw test                # unit tests only
./mvnw -Dtest=Foo test     # a single unit test
./mvnw -Dit.test=FooIT verify   # a single integration test
./mvnw spring-boot:run     # run against local PostgreSQL (db "oms", user/pass oms/oms)
```

- **`*Test`** = fast unit tests (no Spring context), run by Surefire under `test`.
- **`*IT`** = `@SpringBootTest` + MockMvc integration tests, run by Failsafe under `verify`.
  **`verify` is the canonical green-bar command.**
- Tests use **H2 in PostgreSQL mode** with Flyway applied (profile `test`,
  `src/test/resources/application-test.yml`). No external DB needed for tests.

## Architecture (big picture)
Vertical-slice packages under `com.ecomm.oms`, layered
`controller → service → repository → entity`, DTOs as `record`s at the edge.

- **Schema is Flyway-owned**: `src/main/resources/db/migration/V1__schema.sql` is a single
  evolving file (grown per slice); `V2__seed.sql` is data only. Hibernate `ddl-auto=validate`,
  so **entity mappings and the migration must agree** or the context fails to start.
- **Concurrency**: `InventoryService.reserve` locks stock rows with
  `@Lock(PESSIMISTIC_WRITE)` (`StockLevelRepository.lockByProductForUpdate`) → `SELECT … FOR
  UPDATE`. This is the oversell guard; don't replace it with a plain read.
- **Checkout** (`order/CheckoutService`) is one `@Transactional`: validate cart → price →
  reserve → charge → confirm/redeem/close-cart, then publish `OrderPlacedEvent`.
- **Async pipeline** (`order/OrderPipelineListeners`): three independent
  `@Async @TransactionalEventListener(AFTER_COMMIT)` handlers (route, notify, audit) on the
  `applicationTaskExecutor`. They run after commit; integration tests await their effects with
  Awaitility.
- **State machines**: `OrderStatus` and `ReturnStatus` own their transition graphs; the entity
  exposes a single guarded `transitionTo` that throws `409 ILLEGAL_TRANSITION`. Never add a
  raw status setter.
- **Errors**: throw `common/error` exceptions (`NotFoundException` 404, `ConflictException`
  409, `BusinessRuleException` 422, `InsufficientStockException` 409, `PaymentDeclinedException`
  402). `GlobalExceptionHandler` maps them to RFC-7807 `ProblemDetail` with a stable `code`.
- **Money**: always `BigDecimal` via `common/Money` (2dp HALF_UP). Pure order arithmetic lives
  in `pricing/PricingCalculator` (unit-tested without Spring).

## Conventions
- Controllers are thin: validate (`@Valid`), enforce RBAC (`@PreAuthorize` + ownership), map
  to/from DTO records. No business logic or entity serialization.
- Inject the caller with `@CurrentUser AuthPrincipal`; check ownership in the service. Hide
  others' resources with `404`, not `403`.
- Read paths that map lazy to-one associations use `@EntityGraph` so DTO mapping is safe
  outside the transaction.
- Portable DDL only (runs on PostgreSQL and H2): identity columns, `NUMERIC(19,2)`,
  `TIMESTAMP`, `TEXT`, `VARCHAR`. **Avoid reserved-word column names** (e.g. `value`) — they
  break on H2; rename and map with `@Column(name=…)`.
- New work follows the project-local skills in `.claude/skills/`:
  `spring-feature-slice` (scaffold a slice), `integration-test` (MockMvc + JWT helpers via
  `IntegrationTestSupport`/`loginAs`), `api-docs-sync` (keep README API table current),
  `state-machine-transition` (status lifecycles). Prefer reusing these over re-deriving.

## Gotchas
- Run Maven with `JAVA_HOME` set to 21 (above) — the system default may be newer.
- Editing already-applied migrations changes Flyway checksums; fine for fresh DBs/tests, but
  re-running against a persisted PostgreSQL needs a clean DB.
- The concurrency test relies on the H2 URL's `LOCK_TIMEOUT` and a Hikari pool larger than the
  thread count (set in `application-test.yml`).
