# Security Engineering
Use for auth, authorization, sensitive data, APIs, sessions, uploads, payments, external services, and security-sensitive changes.

Baseline: OWASP ASVS principles plus least privilege, deny-by-default, server-side authorization, secure session lifecycle, input validation, output encoding, safe error handling, secret hygiene, rate limiting, abuse resistance, dependency hygiene, and safe logging.

Explicitly test for IDOR/BOLA, cross-family/cross-child access, role escalation, tenant isolation, enumeration, replay, token theft/reuse, CSRF/CORS mistakes, injection, unsafe redirects, upload abuse, and sensitive-data leakage.

Never call a client-side check authorization. Never log passwords, OTPs, refresh tokens, verification tokens, or secrets.
