# E-Commerce Order Management System — Implementation Plan

## Context

This is a greenfield 48-hour take-home assignment. The working directory
(`/Users/stupid/Code/EComm-DMG`) is currently **empty** — there is no existing code to
build on. The goal is to build a feature-rich, production-shaped **e-commerce order
management system** in Spring Boot that demonstrates correct inventory reservation under
concurrency, atomic order placement, a non-blocking downstream pipeline, and the full
catalog → cart → checkout → fulfillment → returns/refunds lifecycle, all behind
role-based access control.

The submission is evaluated on **both breadth and depth** of features and on the quality
of the engineering (concurrency correctness, tests, clean architecture). The deliverable
must include the running code, tests, a `README.md` documenting assumptions, and the
`CLAUDE.md` / skills used during development.

### Locked decisions (confirmed with user)
- **DB:** PostgreSQL 16 for the app; H2 in-memory for tests.
- **Build/runtime:** Maven, Java 21 (LTS), Spring Boot 3.3.x.
- **Auth:** Spring Security + stateless **JWT** bearer tokens; method-level RBAC.
- **Scope:** Full breadth — discounts, taxes, returns/refunds, async pipeline, audit log.

### Secondary decisions (chosen, will document in README)
- **Migrations:** Flyway (`V1__schema.sql`, `V2__seed.sql`).
- **Non-blocking pipeline:** in-process Spring `ApplicationEventPublisher` +
  `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` (no message broker — distributed
  systems are out of scope).
- **Payments:** mock/in-process gateway with configurable outcome + idempotency keys.
- **Mapping:** manual DTO mapping (records) — no MapStruct dependency.
- **Errors:** RFC-7807-style `ProblemDetail` via a global `@RestControllerAdvice`.

---

## Tech Stack

| Concern | Choice |
|---|---|
| Language/Runtime | Java 21 |
| Framework | Spring Boot 3.3.x (Web, Data JPA, Security, Validation, Actuator) |
| DB (app) | PostgreSQL 16 |
| DB (tests) | H2 in-memory (PostgreSQL compatibility mode) |
| Migrations | Flyway |
| Auth | Spring Security + JWT (jjwt) |
| Build | Maven (with wrapper `./mvnw`) |
| Tests | JUnit 5, Spring MockMvc, AssertJ, Mockito |

---

## Domain Model (JPA entities)

Grouped by bounded area. `BaseEntity` provides `id`, `createdAt`, `updatedAt`, and a
JPA `@Version` optimistic-lock column.

- **User & auth:** `User` (email, passwordHash, displayName, `Role` ∈
  {ADMIN, CUSTOMER, WAREHOUSE_STAFF}).
- **Catalog:** `Category` (self-referencing parent → hierarchy), `Product`
  (sku, name, description, basePrice, `taxCategory`, active).
- **Inventory (multi-warehouse):** `Warehouse` (code, name, region/priority);
  `StockLevel` (product × warehouse, `quantityOnHand`, `quantityReserved`, `@Version`) —
  unique constraint on `(product_id, warehouse_id)`; `InventoryReservation`
  (order ref, product, warehouse, quantity, status, expiresAt).
- **Cart:** `Cart` (customer, status) + `CartItem` (product, quantity, unitPriceSnapshot).
- **Order:** `Order` (customer, `OrderStatus`, money fields: subtotal, discountTotal,
  taxTotal, shippingTotal, grandTotal, placedAt) + `OrderItem` (product, quantity,
  unitPrice, lineDiscount, lineTax, allocated warehouse).
- **Payment:** `Payment` (order, amount, `PaymentStatus`, method, gatewayRef,
  `idempotencyKey` unique).
- **Discounts:** `Coupon` (code, type PERCENT/FIXED, value, minOrderAmount, validFrom/To,
  maxRedemptions, timesRedeemed, active).
- **Tax:** `TaxRate` (taxCategory/region → rate) — pricing looks up by product taxCategory.
- **Returns/Refunds:** `ReturnRequest` (order, `ReturnStatus`, reason) + `ReturnItem`;
  `Refund` (return/order, amount, status, gatewayRef).
- **Fulfillment:** `Shipment` (order, warehouse, carrier, trackingNumber, status).
- **Cross-cutting:** `AuditLog` (entityType, entityId, action, actor, timestamp,
  details), `Notification` (customer, type, channel, payload, status — mock send).

### Order lifecycle (state machine, validated on transition)
`PLACED → CONFIRMED → PACKED → SHIPPED → DELIVERED → RETURNED`, plus `CANCELLED`.
Illegal transitions throw a domain exception (HTTP 409). Warehouse staff drive
CONFIRMED→…→DELIVERED; returns open the RETURNED path.

---

## Package / Module Structure

```
com.ecomm.oms
├─ config/         (Async, Jackson, OpenAPI, Flyway)
├─ security/       (SecurityConfig, JwtService, JwtAuthFilter, CurrentUser)
├─ common/         (BaseEntity, error/ProblemDetail handler, Money util, ApiResponse)
├─ user/           (User, Role, AuthController, AuthService)
├─ catalog/        (Category, Product, controllers, services, repos, dto)
├─ inventory/      (Warehouse, StockLevel, Reservation, InventoryService, allocation)
├─ cart/           (Cart, CartItem, CartController, CartService)
├─ pricing/        (PricingService — discount + tax computation, Coupon, TaxRate)
├─ order/          (Order, OrderItem, status machine, CheckoutService, OrderController)
├─ payment/        (Payment, PaymentGateway interface + MockPaymentGateway)
├─ fulfillment/    (Shipment, FulfillmentService, routing, WarehouseStaffController)
├─ returns/        (ReturnRequest, Refund, ReturnService)
├─ audit/          (AuditLog, AuditService, event listeners)
└─ notification/   (Notification, NotificationService, event listeners)
```

Layering per module: `controller` (DTO + validation) → `service` (tx + business rules)
→ `repository` (Spring Data JPA) → `entity`.

---

## Concurrency & Oversell Prevention (centerpiece)

Order placement is a single `@Transactional` unit. Inventory reservation uses
**pessimistic write locking** so two concurrent checkouts cannot both claim the last unit:

- `StockLevelRepository.findForUpdate(productId, warehouseId)` annotated
  `@Lock(LockModeType.PESSIMISTIC_WRITE)` (emits `SELECT … FOR UPDATE`).
- `InventoryService.reserve(productId, qty)` iterates candidate warehouses by priority,
  locks each `StockLevel` row, computes `available = onHand − reserved`, and allocates
  greedily across warehouses (supports split allocation). Throws
  `InsufficientStockException` (409) if total available < requested.
- On successful payment the reservation is **confirmed**: `quantityReserved` released and
  `quantityOnHand` decremented; on failure/rollback the reservation is voided.
- `@Version` optimistic column on `StockLevel` is the second line of defense.

This is proven by a dedicated concurrency test (below).

### Atomic checkout sequence (`CheckoutService.placeOrder`)
1. Load + validate cart (non-empty, products active).
2. `PricingService` computes line prices, applies coupon, computes taxes → totals.
3. `InventoryService.reserve(...)` with pessimistic locks (allocates warehouses).
4. `PaymentGateway.charge(...)` with idempotency key (mock; configurable success).
5. On success: persist `Order` (status PLACED), confirm reservations/decrement stock,
   redeem coupon, mark cart CHECKED_OUT.
6. Publish `OrderPlacedEvent`; return checkout response immediately.

Steps 1–5 are one transaction → cart, inventory, and payment state are atomic.

---

## Non-Blocking Downstream Pipeline

`OrderPlacedEvent` (and lifecycle events) are handled **after commit**, off the request
thread, via `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async` on a dedicated
`ThreadPoolTaskExecutor`:
- **Fulfillment routing** → creates `Shipment`(s), advances order to CONFIRMED.
- **Notifications** → mock customer notification (logged/persisted).
- **Audit logging** → records the action.

The customer's checkout HTTP response does not wait on any of these.

---

## REST API Surface (by role)

- **Auth (public):** `POST /api/auth/register`, `POST /api/auth/login` → JWT.
- **Catalog (read public / write ADMIN):** CRUD `/api/categories`, `/api/products`;
  browse/search `GET /api/products?category=&q=&page=`.
- **Inventory & warehouses (ADMIN):** CRUD `/api/warehouses`, adjust stock
  `POST /api/warehouses/{id}/stock`.
- **Discounts (ADMIN):** CRUD `/api/coupons`; **Tax rates (ADMIN):** `/api/tax-rates`.
- **Cart (CUSTOMER):** `GET/POST/DELETE /api/cart/items`, `POST /api/cart/apply-coupon`.
- **Checkout & orders (CUSTOMER):** `POST /api/checkout`, `GET /api/orders`,
  `GET /api/orders/{id}` (tracking), `POST /api/orders/{id}/cancel`.
- **Returns/refunds (CUSTOMER request / ADMIN approve):** `POST /api/orders/{id}/returns`,
  `POST /api/returns/{id}/approve`, refund issued on receipt.
- **Fulfillment (WAREHOUSE_STAFF):** `POST /api/orders/{id}/fulfillment/status`
  (CONFIRMED→PACKED→SHIPPED→DELIVERED with validation), update shipment tracking.

RBAC enforced via `@PreAuthorize("hasRole('…')")` and ownership checks (a customer may
only read their own orders/cart).

---

## Validation & Error Handling
- Jakarta Bean Validation (`@NotNull`, `@Positive`, `@Email`, …) on request DTOs.
- Global `@RestControllerAdvice` maps domain + validation exceptions to consistent
  `ProblemDetail` JSON (400 validation, 401/403 auth, 404 not found, 409 conflict/illegal
  transition/insufficient stock, 422 business rule).

---

## Testing Strategy
- **Unit tests:** `PricingService` (discount + tax math, coupon edge cases), inventory
  allocation across warehouses, order status-machine legality.
- **Integration tests** (`@SpringBootTest` + MockMvc, H2): auth + RBAC denials, catalog
  admin CRUD, full cart→checkout→order happy path, payment failure rollback (no stock
  decrement), returns→refund flow, fulfillment transitions.
- **Concurrency test (key):** seed 1 unit (or N units) across warehouses; fire K parallel
  checkout threads via `ExecutorService`/`CountDownLatch`; assert **exactly stock-count
  orders succeed**, the rest get 409, and `onHand`/`reserved` end consistent (no oversell).

---

## Deliverables / Docs
- **`README.md`:** problem interpretation, documented assumptions, architecture overview,
  ER summary, setup (Postgres + `./mvnw spring-boot:run`), full API reference, design
  decisions (concurrency, async, why Postgres/JWT), and how to run tests.
- **`CLAUDE.md`:** build/test/run commands, architecture big-picture, conventions (per the
  `/init` request) — written once the structure is real.
- **Seed data** (`V2__seed.sql`): admin user, sample categories/products, warehouses with
  stock, a coupon, tax rates — so reviewers can exercise flows immediately.
- Maven wrapper committed; `.gitignore`; OpenAPI/Swagger UI for manual exploration.

---

## Project-Local Skills (`.claude/skills/`)

These are built early and **used** to develop the slices, then submitted as the "skills
used during development" deliverable. Each is a `SKILL.md` with a focused instruction set.

- **`spring-feature-slice`** — scaffold a full vertical slice (entity, repository, service,
  controller, request/response DTOs, MockMvc test) following the project's layering,
  naming, and error-model conventions. Created in commit 1; used in commits 3–7.
- **`integration-test`** — generate a `@SpringBootTest` + MockMvc test pre-wired with JWT
  auth helpers (`loginAs(ADMIN|CUSTOMER|WAREHOUSE_STAFF)`), happy-path + RBAC-denial
  (401/403) cases. Created in commit 2 (once auth exists); used in every later slice.
- **`api-docs-sync`** — scan controllers and keep the README API reference + OpenAPI tags
  in sync, flagging undocumented endpoints. Created in commit 3; run after each slice and
  in commit 7 for the final README.

---

## Build Order — 7 reviewable commits (tests added/updated each step)

Each commit is a self-contained, reviewable vertical slice with a clear purpose and its
own tests. API contracts (request/response DTOs + OpenAPI annotations) are defined inside
the slice that owns them; commit 1 establishes the cross-cutting contract conventions
(error model, response envelope, validation) that every later slice follows.

**Commit 1 — Project scaffold & contract foundation (S)**
- *Purpose:* runnable skeleton + shared API conventions everything builds on.
- *Scope:* Maven pom + wrapper, `.gitignore`, app config, profiles (postgres / h2-test),
  Flyway baseline migration, `BaseEntity`, global `@RestControllerAdvice` + `ProblemDetail`
  error model, response/validation conventions, OpenAPI/Swagger, Actuator health.
- *Skill:* author `.claude/skills/spring-feature-slice`.
- *Tests:* context-loads test; global error-handler unit test; MockMvc smoke on
  `/actuator/health`.

**Commit 2 — Security & auth (users, roles, JWT) (M)**
- *Purpose:* identity + RBAC contract that gates every protected endpoint.
- *Scope:* `User`/`Role`, `AuthController` (`register`, `login`), `JwtService`,
  `JwtAuthFilter`, `SecurityConfig`, method security, `CurrentUser` resolver.
- *Skill:* author `.claude/skills/integration-test` (JWT/RBAC test helpers).
- *Tests:* JWT issue/validate unit; register+login integration; RBAC denial (401/403) on
  a protected probe endpoint.

**Commit 3 — Catalog, warehouses & inventory model (admin contracts) (M)**
- *Purpose:* product/warehouse/stock domain + admin write APIs + public browse.
- *Scope:* `Category` (hierarchy), `Product`, `Warehouse`, `StockLevel` (+ unique
  constraint, `@Version`), admin CRUD, `GET /api/products` browse/search.
- *Skill:* author `.claude/skills/api-docs-sync`; run after this slice.
- *Tests:* catalog CRUD integration; admin-only write RBAC; product search/pagination;
  stock-adjust integration.

**Commit 4 — Cart & pricing (discounts + taxes) (M)**
- *Purpose:* cart contract + deterministic money math before checkout.
- *Scope:* `Cart`/`CartItem` endpoints, `Coupon`, `TaxRate`, `PricingService`
  (line pricing → discount → tax → totals), apply-coupon.
- *Tests:* `PricingService` unit (percent/fixed discount, min-order, tax, rounding edge
  cases); cart add/update/remove integration; apply-coupon integration.

**Commit 5 — Checkout, payment & inventory reservation (concurrency centerpiece) (L)**
- *Purpose:* atomic order placement + oversell-proof reservation.
- *Scope:* `Order`/`OrderItem`, `Payment` + `MockPaymentGateway` (idempotency),
  `InventoryService.reserve` with pessimistic `SELECT … FOR UPDATE` + multi-warehouse
  allocation, transactional `CheckoutService.placeOrder`.
- *Tests:* checkout happy path; payment-failure rollback (no stock decrement);
  **concurrency oversell test** (K parallel checkouts → exactly stock-count succeed, rest
  409, no negative stock).

**Commit 6 — Fulfillment lifecycle & async pipeline (M)**
- *Purpose:* post-checkout lifecycle + non-blocking downstream work.
- *Scope:* order status machine transitions, `Shipment`, fulfillment routing,
  `OrderPlacedEvent` + `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`,
  `NotificationService`, `AuditService`, warehouse-staff endpoints.
- *Tests:* legal/illegal transition (409) tests; warehouse-staff RBAC; async listener
  produces audit + notification records (Awaitility).

**Commit 7 — Returns, refunds & docs/seed polish (M)**
- *Purpose:* close the lifecycle + make the repo reviewer-ready.
- *Scope:* `ReturnRequest`/`ReturnItem`, approve flow, `Refund`, restock-on-return;
  `README.md` (assumptions, architecture, API, setup), `CLAUDE.md`, `V2__seed.sql`
  (admin, catalog, warehouses+stock, coupon, tax rates).
- *Tests:* return→approve→refund integration; restock assertion; full end-to-end smoke.

---

## Verification
1. **Build & unit/integration tests:** `./mvnw test` (uses H2; must pass green).
2. **Run app:** start local Postgres, `./mvnw spring-boot:run`; Flyway applies schema+seed.
3. **Smoke the flows** via curl/Swagger: login as admin → create product+stock; login as
   customer → add to cart → apply coupon → checkout → track order; warehouse staff →
   advance fulfillment; customer → request return → admin approve → refund.
4. **Prove no oversell:** run the concurrency test and/or hit `/api/checkout` with a
   parallel curl loop against a 1-unit product; confirm exactly one success, rest 409,
   and stock ends at 0 with no negative quantities.
