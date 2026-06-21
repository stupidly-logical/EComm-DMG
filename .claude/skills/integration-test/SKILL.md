---
name: integration-test
description: Write a @SpringBootTest + MockMvc integration test for an OMS endpoint, pre-wired with JWT auth helpers (loginAs(ADMIN|CUSTOMER|WAREHOUSE_STAFF)) covering a happy path plus RBAC-denial (401/403) cases. Use when adding or changing any secured REST endpoint.
---

# Integration Test

Write a black-box HTTP test for an OMS endpoint that exercises the real security chain,
controllers, services, and H2 database.

## Setup
Extend `com.ecomm.oms.support.IntegrationTestSupport`. It boots the context
(`@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")`) and exposes:
- `mockMvc`, `objectMapper`, `userRepository`, `passwordEncoder`, `jwtService`
- `loginAs(Role)` → an `Authorization` header value for a fresh user of that role
- `createUser(Role)` → the persisted `User` (use when you need its id for ownership tests)
- `bearer(User)` → that specific user's header value
- `toJson(obj)` → request-body serialisation

Name the class `<Feature>IT` so Maven Failsafe runs it under `./mvnw verify`.

## Always cover
1. **Happy path** — valid request with the *correct* role → 2xx + asserted body
   (`jsonPath`). For writes, assert the persisted side effect via the repository.
2. **Unauthenticated** — no `Authorization` header → **401** with a `ProblemDetail`
   (`$.code`, `$.status`).
3. **Wrong role** — a token for a role lacking permission → **403**.
4. **Validation** — a malformed body → **400** with `$.code == "VALIDATION_FAILED"`.
5. **Not found / conflict** where applicable → 404 / 409 with the expected `$.code`.

## Conventions
- Import statics: `MockMvcRequestBuilders.*`, `MockMvcResultMatchers.*`.
- Send `Authorization` with `org.springframework.http.HttpHeaders.AUTHORIZATION`.
- Set `contentType(MediaType.APPLICATION_JSON)` and `.content(toJson(request))` on writes.
- Assert the error contract on failures: `jsonPath("$.code").value(...)` and
  `jsonPath("$.status").value(<code>)`.
- Keep assertions independent of global row counts (the H2 context is shared across the
  class); assert on the specific entities the test created.

## Skeleton
```java
class WidgetIT extends IntegrationTestSupport {

    @Test
    void adminCreatesWidget() throws Exception {
        mockMvc.perform(post("/api/widgets")
                .header(AUTHORIZATION, loginAs(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new CreateWidgetRequest("W-1", "Widget"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sku").value("W-1"));
    }

    @Test
    void customerCannotCreateWidget() throws Exception {
        mockMvc.perform(post("/api/widgets")
                .header(AUTHORIZATION, loginAs(Role.CUSTOMER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new CreateWidgetRequest("W-1", "Widget"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/widgets/1"))
            .andExpect(status().isUnauthorized());
    }
}
```
