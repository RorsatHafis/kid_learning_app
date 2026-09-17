# YulMe Admin MVP drop-in

Copy `YulMe-Backend/src/main/java/com/platform/admin` into the same path in your project.

No database migration is required.

## First local ADMIN

Public registration intentionally rejects staff roles. For a clean local database, start the backend once with:

`PLATFORM_BOOTSTRAP_ADMIN_ENABLED=true`

`PLATFORM_BOOTSTRAP_ADMIN_EMAIL=admin@example.com`

`PLATFORM_BOOTSTRAP_ADMIN_PASSWORD=<12+ characters>`

The bootstrap creates an ADMIN only if no ADMIN exists. After it starts successfully, disable/remove `PLATFORM_BOOTSTRAP_ADMIN_ENABLED` and restart.

## Endpoints

`POST /api/v1/admin/staff` — ADMIN-only creation of TEACHER or PRINCIPAL accounts.

Body:
```json
{"email":"teacher@example.com","password":"TeacherPassword123!","role":"TEACHER"}
```

`GET /api/v1/admin/staff` — ADMIN-only staff listing.

Existing school management, teacher authoring, and challenge endpoints are reused; this package does not duplicate those systems.

## Verification

The source was prepared against the current YulMe checkpoint. Full Maven compilation was blocked here because the Maven wrapper attempted to download Maven 3.9.16 and external dependency access failed.
