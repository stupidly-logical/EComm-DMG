# E-Commerce Order Management System

A production-shaped order-management backend in **Spring Boot 3.3 / Java 21**. It covers the
full lifecycle — catalog → cart → checkout → fulfillment → returns/refunds — behind JWT
role-based access control, with **concurrency-safe inventory reservation** as the centerpiece
and a **non-blocking downstream pipeline** for post-checkout work.

---

## Problem interpretation

Build a feature-rich, correct order-management system. Evaluation weighs both **breadth**
(catalog, discounts, taxes, multi-warehouse inventory, payments, fulfillment, returns, audit)
and **engineering depth** (no oversell under concurrency, atomic checkout, clean layering,
meaningful tests). The two hard problems taken seriously here are:

1. **No oversell** — two shoppers must never both buy the last unit.
2. **Atomic checkout** — cart, inventory, and payment either all commit or all roll back; a
   declined payment leaves no order and no decremented stock.

---

## Tech stack

| Concern | Choice |
|---|---|
| Language / runtime | Java 21 (LTS) |
| Framework | Spring Boot 3.3 (Web, Data JPA, Security, Validation, Actuator) |
| App database | PostgreSQL 16 |
| Test database | H2 in-memory, PostgreSQL-compatibility mode |
| Migrations | Flyway (`V1__schema.sql`, `V2__seed.sql`) |
| Auth | Spring Security + stateless JWT (jjwt 0.12) |
| Build | Maven (wrapper committed) |
| Tests | JUnit 5, Spring MockMvc, AssertJ, Awaitility |
| Docs | springdoc-openapi (Swagger UI) |

---

## Architecture

Vertical-slice packages under `com.ecomm.oms`, each layered
`controller → service → repository → entity` with request/response **records** at the edge:

```
config/        Async executor, OpenAPI, @CurrentUser MVC wiring
security/      JWT issue/verify, auth filter, SecurityConfig, RBAC, @CurrentUser
common/        BaseEntity (id, @Version, timestamps), Money, PageResponse, RFC-7807 errors
user/          User + Role, registration/login, admin user directory
catalog/       Category (hierarchy), Product, admin CRUD + public browse/search
inventory/     Warehouse, StockLevel, InventoryReservation, reservation engine
pricing/       Coupon, TaxRate, pure PricingCalculator, PricingService
cart/          Cart + CartItem, cart operations, apply-coupon
order/         Order (+ state machine), OrderItem, CheckoutService, OrderService
payment/       PaymentGateway + MockPaymentGateway (idempotent), PaymentService
fulfillment/   Shipment, FulfillmentService, warehouse-staff endpoints
returns/       ReturnRequest/ReturnItem, Refund, ReturnService
audit/         AuditLog + AuditService
notification/  Notification + NotificationService (mock send)
```

### Concurrency & oversell prevention (centerpiece)

Order placement is a single `@Transactional` unit. `InventoryService.reserve` takes a
**pessimistic write lock** on every stock row for the product before reading availability:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM StockLevel s JOIN s.warehouse w WHERE s.product.id = :productId ORDER BY w.priority, w.id")
List<StockLevel> lockByProductForUpdate(Long productId);
```

This emits `SELECT … FOR UPDATE`, so two concurrent checkouts for the last unit **serialize**
on the row locks — the first reserves it, the second sees zero available and gets a `409`.
Allocation is greedy across warehouses by priority and may split a line. Rows are always
locked in the same `(priority, id)` order to avoid deadlocks. `@Version` on `StockLevel` is a
second line of defence. Proven by `OversellConcurrencyIT`: 12 simultaneous checkouts against
3 units leave exactly 3 orders and zero stock, every time.

### Atomic checkout sequence (`CheckoutService.placeOrder`)

1. Load + validate the active cart (non-empty, products active).
2. Price it (`PricingService`): line prices → coupon discount → per-line tax → totals.
3. Reserve inventory under pessimistic locks (holds units; may split warehouses).
4. Charge payment (idempotent; a decline throws → whole transaction rolls back).
5. On success: persist the order `PLACED`, confirm reservations (decrement on-hand), redeem
   the coupon, mark the cart `CHECKED_OUT`.
6. Publish `OrderPlacedEvent` and return immediately.

### Non-blocking downstream pipeline

`OrderPlacedEvent` is handled **after commit, off the request thread** by three independent
`@Async @TransactionalEventListener(AFTER_COMMIT)` methods on a dedicated executor:
fulfillment routing (create shipments, advance `PLACED → CONFIRMED`), customer notification,
and audit logging. The checkout response never waits on them, and one failing does not
suppress the others.

### Order lifecycle (state machine)

`PLACED → CONFIRMED → PACKED → SHIPPED → DELIVERED → RETURNED`, plus `CANCELLED` from
`PLACED`/`CONFIRMED`. The enum owns its transition graph; `Order.transitionTo` is the only
mutator and rejects illegal moves with `409 ILLEGAL_TRANSITION`. Returns use the same pattern
(`REQUESTED → APPROVED → REFUNDED`, or `REJECTED`).

---

## Data model (summary)

```
User(role)                       Coupon(type, value, min, window, cap)
Category ──parent──┐             TaxRate(taxCategory, region → ratePercent)
   └───────────────┘
Product ── Category              Cart 1─* CartItem ── Product
Warehouse 1─* StockLevel *─1 Product   (unique product+warehouse; onHand/reserved/@Version)
Order 1─* OrderItem ── Product         (money snapshot; allocated warehouse)
Order 1─* InventoryReservation ── Warehouse, Product
Order 1─* Payment(idempotencyKey unique)
Order 1─* Shipment ── Warehouse
ReturnRequest 1─* ReturnItem ── OrderItem ;  ReturnRequest 1─1 Refund
AuditLog ;  Notification
```

---

## Running it

### Prerequisites
- JDK 21, and a PostgreSQL 16 instance for running the app (tests need only H2).

### Database
```bash
createdb oms
# default creds (override via env): user "oms" / password "oms"
```
Configure via env if needed: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`.

### Run
```bash
./mvnw spring-boot:run
```
Flyway applies `V1__schema.sql` then `V2__seed.sql` on startup.

- API base: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html` (use **Authorize** with a login token)
- Health: `http://localhost:8080/actuator/health`

### Seeded logins
| Email | Password | Role |
|---|---|---|
| `admin@oms.local` | `Admin123!` | ADMIN |
| `staff@oms.local` | `Staff123!` | WAREHOUSE_STAFF |
| `customer@oms.local` | `Customer123!` | CUSTOMER |

Seed also includes categories, four products, two warehouses with stock (headphones split
5+3 across both), tax rates (`STANDARD` 8.25%, `REDUCED` 2%, `NONE` 0%), and coupons
`WELCOME10` (10% off, min $20) and `SAVE5` ($5 off).

---

## Tests

```bash
./mvnw verify     # unit (*Test, Surefire) + integration (*IT, Failsafe) — the full suite
./mvnw test       # unit tests only
```

All integration tests run against H2 with Flyway applied (the same migrations the app uses).
Highlights: `OversellConcurrencyIT` (the oversell proof), `PricingCalculatorTest` (discount
allocation, tax rounding), `CheckoutIT` (atomicity + idempotency), `FulfillmentIT` (async
pipeline via Awaitility), `ReturnIT` (request→approve→refund→restock), `SmokeIT` (seed
end-to-end).

---

## API reference

`ADMIN` / `CUSTOMER` / `WAREHOUSE_STAFF` are roles; **public** needs no token. All errors are
RFC-7807 `ProblemDetail` JSON with a stable `code`, `status`, and `timestamp`.

### Auth — `/api/auth`
| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/api/auth/register` | public | Register a customer; returns a JWT |
| POST | `/api/auth/login` | public | Exchange credentials for a JWT |
| GET | `/api/auth/me` | authenticated | Current user's profile |
| GET | `/api/users` | ADMIN | List all users |

### Catalog — public read, admin write
| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/categories` · `/api/categories/{id}` | public | List / get categories |
| POST · PUT · DELETE | `/api/categories` · `/{id}` | ADMIN | Create / update / delete category |
| GET | `/api/products?category=&q=&page=&size=` | public | Browse/search active products |
| GET | `/api/products/{id}` | public | Get a product |
| POST · PUT · DELETE | `/api/products` · `/{id}` | ADMIN | Create / update / delete product |

### Inventory & pricing — admin
| Method | Path | Access | Description |
|---|---|---|---|
| GET · POST · PUT · DELETE | `/api/warehouses` · `/{id}` | ADMIN | Warehouse CRUD |
| POST | `/api/warehouses/{id}/stock` | ADMIN | Set on-hand for a product |
| GET · POST | `/api/coupons` · GET `/{code}` | ADMIN | List / create / get coupons |
| GET · POST · PUT | `/api/tax-rates` · `/{id}` | ADMIN | List / create / update tax rates |

### Cart — customer
| Method | Path | Description |
|---|---|---|
| GET | `/api/cart` | View the priced cart |
| POST | `/api/cart/items` | Add a product (increments if present) |
| PUT | `/api/cart/items/{productId}` | Set quantity |
| DELETE | `/api/cart/items/{productId}` | Remove a line |
| POST | `/api/cart/apply-coupon` | Apply a coupon |
| DELETE | `/api/cart/coupon` | Remove the coupon |

### Checkout, orders & returns
| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/api/checkout` | CUSTOMER | Place an order from the active cart |
| GET | `/api/orders` | CUSTOMER | List own orders |
| GET | `/api/orders/{id}` | CUSTOMER (own) / ADMIN / STAFF | Order detail + tracking |
| POST | `/api/orders/{id}/cancel` | CUSTOMER (own) / ADMIN | Cancel + restock (before shipped) |
| POST | `/api/orders/{id}/returns` | CUSTOMER (own) | Request a return (delivered orders) |
| GET | `/api/returns/{id}` | CUSTOMER (own) / ADMIN | Get a return |
| POST | `/api/returns/{id}/approve` | ADMIN | Approve → restock, refund, order RETURNED |
| POST | `/api/returns/{id}/reject` | ADMIN | Reject a return |

### Fulfillment — `/api/orders/{orderId}`
| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/fulfillment/status` | WAREHOUSE_STAFF | Advance PACKED → SHIPPED → DELIVERED |
| PUT | `/shipments/{shipmentId}/tracking` | WAREHOUSE_STAFF | Set carrier + tracking |
| GET | `/shipments` | CUSTOMER (own) / ADMIN / STAFF | List shipments for an order |

---

## Design decisions & assumptions

- **PostgreSQL + pessimistic locking** for the reservation hot path — `SELECT … FOR UPDATE`
  is the clearest correct primitive for "don't sell the last unit twice"; `@Version` backs it up.
- **One evolving `V1__schema.sql`** owns the schema (grown slice-by-slice during development);
  `V2__seed.sql` is data only. Reviewers start from a clean database. DDL is portable across
  PostgreSQL and H2 (identity columns, `NUMERIC(19,2)`, `TIMESTAMP`, `TEXT`); reserved words
  are avoided (`coupons.discount_value`).
- **Tests run Flyway against H2** in PostgreSQL mode, so the real migrations are exercised on
  every build rather than relying on Hibernate `ddl-auto` (which is set to `validate`).
- **Stateless JWT** (no server sessions) with method-level `@PreAuthorize` and explicit
  ownership checks; non-owners get `404` (not `403`) so resource existence isn't leaked.
- **In-process async pipeline** via Spring events + `@Async` rather than a message broker —
  distributed messaging is out of scope; the seam (`OrderPlacedEvent`) is where a broker
  would slot in.
- **Mock payment gateway**, idempotent by key (token `"DECLINE"` forces a decline so the
  rollback path is testable); checkout accepts an `idempotencyKey` for safe retries.
- **Money** is always `BigDecimal` at 2dp `HALF_UP`; order-level discounts are allocated to
  lines with the rounding remainder absorbed by the last line so totals reconcile exactly.
- **Free shipping** is assumed (the `shippingTotal` field exists for completeness); a single
  store **region** (`oms.pricing.region`, default `US`) selects tax rates, and a product with
  no matching rate is treated as tax-free.
- **Returns** are allowed only on `DELIVERED` orders; approval restocks to the line's
  fulfilling warehouse and refunds the full line total (proportional for partial returns).

See `.claude/skills/` for the project-local skills authored and used during development:
`spring-feature-slice`, `integration-test`, `api-docs-sync`, `state-machine-transition`.
