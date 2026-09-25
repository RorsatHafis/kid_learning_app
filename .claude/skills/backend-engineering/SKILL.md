# Backend Engineering
Use for Spring Boot/domain implementation.

Workflow: trace controller → guard → service → repository → entity/migration → DTO; inspect transactions and concurrency; identify invariant; implement smallest fix; verify.

Standards: explicit transaction boundaries, correct propagation, idempotency where retries exist, server-derived authoritative values, deterministic queries, ownership checks from trusted identity, safe exception mapping, no business rules in controllers.

For learning flows preserve evidence → mastery → adaptive/review/progress/insight semantics. Never substitute latest version for an explicitly pinned version.
