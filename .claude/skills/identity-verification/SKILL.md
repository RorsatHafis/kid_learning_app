# Identity Verification
Email/phone verification must represent real verified ownership, not a UI flag.

Email: secure random token, hash where appropriate, expiry, single-use, resend invalidation/rate limit, verified timestamp/state, safe change-email flow, enumeration resistance.

Phone: cryptographically secure OTP, short TTL, single use, attempt limit, resend throttling/invalidation, no OTP logging, provider delivery status, abuse/rate limits.

Separate email/phone verification from strong authentication/MFA. Do not invent provider integration; inspect the existing boundary and surface missing production dependencies.
