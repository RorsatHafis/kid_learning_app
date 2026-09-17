# Admin MVP package

Drop the `com/platform/admin` package under:
`YulMe-Backend/src/main/java/com/platform/admin/`

This adds:
- `POST /api/v1/admin/staff` — ADMIN-only provisioning of TEACHER or PRINCIPAL accounts.
- `GET /api/v1/admin/staff` — ADMIN-only staff listing.
- Optional first-admin bootstrap via `platform.bootstrap-admin.*`.

Bootstrap is disabled by default. For local setup only, set:

```yaml
platform:
  bootstrap-admin:
    enabled: true
    email: admin@example.com
    password: change-me-before-use
```

After the first ADMIN is created, disable bootstrap again. Do not commit real credentials.

Existing school, teacher-authoring, and challenge services are reused; this package does not duplicate them.
