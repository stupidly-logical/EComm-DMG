---
name: spring-feature-slice
description: Scaffold a complete Spring Boot vertical slice (entity, repository, service, controller, request/response DTOs, and a MockMvc test) following this project's layering, naming, error-model, and migration conventions. Use when adding a new domain feature/module to the OMS codebase.
---

# Spring Feature Slice

Scaffold one **vertical slice** for the OMS codebase the way the rest of the project is
built. A slice lives in a single package under `com.ecomm.oms.<module>` and flows
`controller → service → repository → entity`, with DTOs and validation at the edge.

## When to use
Adding a new domain capability (e.g. `coupon`, `shipment`). Not for cross-cutting infra.

## Layout to produce
```
com.ecomm.oms.<module>/
├─ <Entity>.java                 // JPA entity extends common.BaseEntity
├─ <Entity>Repository.java       // extends JpaRepository<Entity, Long>
├─ <Module>Service.java          // @Service @Transactional; business rules live here
├─ <Module>Controller.java       // @RestController; thin; maps DTO<->entity
└─ dto/
   ├─ <Action>Request.java       // record + Jakarta validation annotations
   └─ <Entity>Response.java      // record; static from(entity) factory
```

## Conventions (match these exactly)
- **Entity**: extend `common.BaseEntity` (gives id, version, createdAt, updatedAt). Map
  columns explicitly with `@Column`; enums as `@Enumerated(EnumType.STRING)`; money as
  `BigDecimal` `NUMERIC(19,2)`. Add table + columns to `db/migration/V1__schema.sql`
  using portable DDL (identity columns, `NUMERIC(19,2)`, `TIMESTAMP`, `TEXT`,
  `VARCHAR` — runs on both PostgreSQL and H2). `ddl-auto=validate`, so the migration and
  the entity must agree.
- **Repository**: Spring Data interface. For locked reads use
  `@Lock(LockModeType.PESSIMISTIC_WRITE)` + `@Query`.
- **Service**: `@Service`; annotate write methods `@Transactional` (read paths
  `@Transactional(readOnly = true)`). Throw `common.error.NotFoundException` (404),
  `ConflictException` (409), `BusinessRuleException` (422) — never raw
  `ResponseStatusException`. Keep controllers free of business logic.
- **Controller**: `@RestController` + `@RequestMapping("/api/<plural>")`. Validate request
  bodies with `@Valid`. Enforce RBAC with `@PreAuthorize("hasRole('…')")` and ownership
  checks for customer-owned resources. Return response records, never entities. Paginated
  endpoints return `common.PageResponse<T>` via `PageResponse.from(page)`.
- **DTOs**: Java `record`s. Requests carry Jakarta validation (`@NotNull`, `@Positive`,
  `@Email`, `@Size`). Responses expose a `static from(Entity)` factory; do not serialise
  entities directly.
- **OpenAPI**: annotate controllers with `@Tag` and key operations with `@Operation` so the
  generated docs and README API reference stay meaningful.

## Test to produce
A MockMvc integration test using the `integration-test` skill's JWT helpers once auth
exists (`loginAs(ADMIN|CUSTOMER|WAREHOUSE_STAFF)`): a happy-path case plus at least one
RBAC-denial (401/403) case. Name it `<Module>IT` so Failsafe runs it under `./mvnw verify`.
Pure business logic (pricing/allocation/state-machine) also gets a fast unit test named
`<Class>Test` (Surefire, no Spring context).

## Checklist before done
- [ ] Migration row added to `V1__schema.sql`; entity columns match exactly.
- [ ] Service holds all business rules and transaction boundaries.
- [ ] Controller is thin, validated, RBAC-guarded, returns DTOs.
- [ ] `./mvnw verify` is green.
