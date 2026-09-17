# YulMe Final Complete Checkpoint

This archive is the complete project assembled from the latest full MVP source plus the accumulated UI/role/localization refinements.

## Role responsibility
- Parent: family and learner experience.
- Teacher: Teacher Studio.
- Principal: School Leadership; self-registration is pending until admin approval.
- Admin: Admin Console, staff/school administration, platform content and challenges.

## Security/workflow refinements
- Principal public registration creates PENDING_VERIFICATION and does not issue a session.
- Admin can list and approve pending principals.
- Pending accounts cannot authenticate through LoginService.
- Admin content entry is separate in the frontend from Teacher Studio.
- Learner hero uses yulme-hero.png; mascot uses yulme-cheetah-transparent.png; logo uses yulme-logo-transparent.png.

## Verification
- JSON parsing and en/km key parity verified: 303/303.
- Stale legacy mascot/logo references removed from frontend source.
- Full Maven/TypeScript runtime builds were not executable in this offline environment because dependencies were unavailable.
