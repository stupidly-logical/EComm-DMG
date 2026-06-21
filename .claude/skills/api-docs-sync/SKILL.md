---
name: api-docs-sync
description: Keep the README API reference and OpenAPI annotations in sync with the actual REST controllers. Scans @RestController endpoints, flags any that are undocumented or mis-described, and updates the README endpoint table. Use after adding/changing endpoints in an OMS slice and before finalizing docs.
---

# API Docs Sync

Keep the human-facing API reference (README) and the generated OpenAPI docs aligned with
the controllers that actually exist.

## Procedure
1. **Enumerate endpoints.** Find every mapping across the controllers:
   ```
   grep -rEn "@(Get|Post|Put|Patch|Delete)Mapping|@RequestMapping" src/main/java
   ```
   For each, record: HTTP method, full path (class `@RequestMapping` + method path),
   required role (`@PreAuthorize` on the method or class, or public per `SecurityConfig`),
   request body DTO, and success status.

2. **Check OpenAPI metadata.** Every controller should have a class-level `@Tag`; every
   endpoint a concise `@Operation(summary = …)`. Flag and add any that are missing.

3. **Reconcile the README.** Compare against the "API Reference" table in `README.md`:
   - Add rows for endpoints present in code but missing from the docs.
   - Remove/fix rows for endpoints that no longer exist or whose method/path/role changed.
   - Keep the table grouped by resource and ordered the way `SecurityConfig` reasons about
     access (public → customer → staff → admin).

4. **Verify access claims.** Cross-check each documented role against `SecurityConfig`
   matchers and method `@PreAuthorize`. The docs must not claim an endpoint is public if the
   filter chain authenticates it (or vice-versa).

5. **Report.** Output a short diff summary: endpoints added, removed, changed, and any
   still missing `@Operation`/`@Tag`.

## Conventions
- One README table row per endpoint: `| METHOD | path | role | description |`.
- Path params in braces (`/api/products/{id}`); query params noted in the description.
- Don't invent endpoints — document only what the controllers expose.
