---
name: api-docs-sync
description: Keep the API documentation in sync with the actual REST controllers — both the README endpoint table AND the in-code OpenAPI/Swagger annotations (tags, operations, per-operation security, error responses, parameter docs). Use after adding or changing any endpoint in an OMS slice, and before finalizing docs.
---

# API Docs Sync

Keep both faces of the API documentation aligned with the controllers that actually exist:
1. the human-facing **README** "API Reference" table, and
2. the generated **OpenAPI** document (`/v3/api-docs`) / Swagger UI (`/swagger-ui.html`).

## 1. Enumerate endpoints
Find every mapping across the controllers:
```
grep -rEn "@(Get|Post|Put|Patch|Delete)Mapping|@RequestMapping" src/main/java
```
For each, record: HTTP method, full path (class `@RequestMapping` + method path), required
role (`@PreAuthorize` on the method or class, or public per `SecurityConfig`), request body
DTO, success status, and which error statuses it can return.

## 2. OpenAPI annotations (in-code)
Every controller and endpoint must carry accurate Swagger annotations so the generated doc is
correct. Conventions:

- **Tag & summary**: every controller has a class-level `@Tag(name, description)`; every
  endpoint a concise `@Operation(summary = …)` (add `description` only when it adds value).
- **Security accuracy**: the global JWT bearer requirement is declared once in
  `config/OpenApiConfig.java`, so *secured* endpoints need no per-op security annotation.
  **Public** endpoints (permit-all in `SecurityConfig`, e.g. register/login, catalog GETs)
  MUST override it with an empty `@SecurityRequirements` (from
  `io.swagger.v3.oas.annotations.security`) — otherwise Swagger UI shows a false lock.
- **Error responses**: document the non-2xx outcomes the op can actually produce with
  `@ApiResponse(responseCode = "…", ref = "#/components/responses/<Name>")`, referencing the
  reusable responses defined in `OpenApiConfig` (`BadRequest` 400, `Unauthorized` 401,
  `Forbidden` 403, `NotFound` 404, `Conflict` 409, `UnprocessableEntity` 422). Rules of thumb:
  - secured op → 401 + 403;
  - by-id `get`/`update`/`delete`/cancel/approve/reject → 404;
  - create with a unique key, or an illegal state-machine transition → 409;
  - business-rule op (checkout, apply-coupon, request-return) → 422;
  - any `@Valid @RequestBody` op → 400.
  Don't document a status the handler can't return.
- **Parameters**: every path/query param gets `@Parameter(description = …)` (e.g.
  `ProductController.browse`'s `category`/`q`/`page`/`size`, and `{id}`/`{orderId}` path vars).
- **Shared definitions live in `OpenApiConfig`**: the `ProblemDetail` schema and the reusable
  `responses` components. Never inline a duplicate error schema in a controller; add new
  shared pieces to `OpenApiConfig` and `ref` them.
- **Request schemas are auto-inferred**: springdoc reads the DTO records' Jakarta validation
  (`@NotNull`/`@Email`/`@Size`/`@Positive`…) to mark fields required/constrained — no manual
  `@Schema` needed for the pragmatic baseline.

## 3. Reconcile the README
Compare against the "API Reference" table in `README.md`:
- Add rows for endpoints present in code but missing from the docs; fix/remove rows whose
  method/path/role changed or that no longer exist.
- Keep it grouped by resource and ordered the way `SecurityConfig` reasons about access
  (public → customer → staff → admin).
- One row per endpoint: `| METHOD | path | role | description |`. Path params in braces; query
  params noted in the description.

## 4. Verify access claims
Cross-check each documented role against `SecurityConfig` matchers and method `@PreAuthorize`.
The README and the OpenAPI security must not claim an endpoint is public if the filter chain
authenticates it (or vice-versa).

## 5. Verify the generated doc
- `OpenApiDocsIT` (MockMvc against `/v3/api-docs`) must stay green: it asserts the doc
  generates, the bearer scheme and reusable responses exist, public ops have no security, and
  secured ops list bearer. Update it when you add a controller.
- Optionally regenerate locally and eyeball Swagger UI: public endpoints show no lock, secured
  ones do, error responses are listed.

## 6. Report
Output a short diff summary: endpoints added/removed/changed, annotations added, and anything
still missing a `@Tag`/`@Operation`/security override/error response.

## Don't
- Invent endpoints — document only what the controllers expose.
- Duplicate the error schema per controller — `ref` the shared components.
- Leave a public endpoint inheriting the global bearer requirement.
