# Observability
Production behavior must be diagnosable without leaking sensitive data.

Use structured logs, correlation/request IDs, useful domain events/metrics where appropriate, safe error categories, and actionable failure context. Never log credentials, tokens, OTPs, verification links, or unnecessary child/family personal data.

Observability changes should answer: what failed, where, for which safe correlation ID, and whether a retry is safe.
