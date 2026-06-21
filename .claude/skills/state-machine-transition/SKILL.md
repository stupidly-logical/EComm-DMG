---
name: state-machine-transition
description: Implement a validated entity state machine (e.g. order lifecycle, return lifecycle, reservation lifecycle) where illegal transitions are rejected with a 409. Use when adding or changing a status enum that must only move along an allowed graph, or when a service mutates such a status.
---

# State-Machine Transition

Model a lifecycle as a status enum that owns its own legal transitions, so every code path
that changes the status goes through one guarded gate and illegal moves surface as a 409
`ConflictException` — never a silent bad write.

## Pattern

1. **Enum owns the graph.** Each constant declares which statuses it may move to. Keep the
   allowed-set on the enum, not scattered across services.

   ```java
   public enum OrderStatus {
       PLACED, CONFIRMED, PACKED, SHIPPED, DELIVERED, RETURNED, CANCELLED;

       private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
           PLACED,    EnumSet.of(CONFIRMED, CANCELLED),
           CONFIRMED, EnumSet.of(PACKED, CANCELLED),
           PACKED,    EnumSet.of(SHIPPED),
           SHIPPED,   EnumSet.of(DELIVERED),
           DELIVERED, EnumSet.of(RETURNED),
           RETURNED,  EnumSet.noneOf(OrderStatus.class),
           CANCELLED, EnumSet.noneOf(OrderStatus.class));

       public boolean canTransitionTo(OrderStatus target) {
           return ALLOWED.getOrDefault(this, Set.of()).contains(target);
       }

       public boolean isTerminal() {
           return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
       }
   }
   ```

2. **Entity exposes one mutator** that enforces the gate — no setter for the raw status:

   ```java
   public void transitionTo(OrderStatus target) {
       if (!status.canTransitionTo(target)) {
           throw new ConflictException(
               "Cannot move order from " + status + " to " + target,
               "ILLEGAL_TRANSITION");
       }
       this.status = target;
   }
   ```

3. **Service calls only `transitionTo`** inside a `@Transactional` method. Role checks
   (which actor may drive which transition) live at the controller/service boundary via
   `@PreAuthorize`; the *legality* of the move lives on the enum.

## Conventions
- Error code is always `ILLEGAL_TRANSITION`, status 409, so clients can branch on it.
- Terminal states have an empty allowed-set; `isTerminal()` is the single source of truth.
- One unit test per machine asserting (a) every legal edge succeeds and (b) a representative
  illegal edge throws — table-driven over the enum, no Spring context needed.
- Reuse this for: order lifecycle, fulfillment-driven order moves, return-request lifecycle.
  Do not duplicate the allowed-graph in more than one place.
