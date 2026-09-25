# Bookeyond Kid — Engineering Constitution

## Mission

Build Bookeyond Kid as a production-grade children's learning platform that can be genuinely used in real life:

- trustworthy to parents
- engaging and safe for children
- useful to teachers
- operationally manageable by administrators
- architecturally ready for school expansion

The goal is not merely to create a prototype, technical demonstration, or collection of working endpoints.

The goal is:

> Build the smallest product scope that can be genuinely trusted and used in real life.

Professional does not mean building every possible feature.

Professional means that features we choose to include are:

- functionally correct
- secure
- maintainable
- accessible
- understandable
- resilient
- honestly verified
- appropriate for their intended users

A feature may be intentionally deferred.

A feature must not be intentionally fake, misleading, insecure, or knowingly broken.

---

# 1. PERMANENT ARCHITECTURE

Backend is authoritative for:

- business rules
- security
- authorization
- learning state
- evidence
- mastery
- progression
- adaptive decisions
- persistence
- server-derived outcomes

Frontend is responsible for:

- presentation
- interaction
- accessibility
- responsive behavior
- animation and motion
- client-side UI state
- communicating backend state clearly

Frontend must not recreate authoritative backend rules.

Learning loop:

**Content → Learning Path → Activity → Question → Evidence → Skill Mastery → Adaptive Decision / Review / Progression / Insight**

Core product pillars:

- Adaptive Engine
- Progression
- Parent Insight
- Smart Review

Roles:

- CHILD
- PARENT
- TEACHER
- PRINCIPAL
- ADMIN

Current product priority:

**Children + Parents**

School capabilities are important but must not turn into premature school-ERP scope.

The family product and school product should share the underlying learning platform while respecting permissions, privacy, and context.

---

# 2. CORE PRODUCT THESIS

Bookeyond Kid follows this principle:

> **Content tells what can be learned. Teacher tells how. Platform decides what this child needs next.**

A stronger product framing is:

> **Same content + different evidence = different learning path + different explanation for the adult.**

Bookeyond Kid should not merely track whether a child completed an activity.

The system should connect:

**Child learns → child demonstrates understanding → platform captures evidence → learning adapts → parent understands progress**

Completion is not automatically mastery.

The platform must distinguish activity completion from actual learning evidence and skill development.

---

# 3. NON-NEGOTIABLE ENGINEERING RULES

1. **Inspect before modifying.**

2. **Verify repository behavior.**
   Do not trust comments, old documentation, screenshots, previous agent claims, or assumptions over actual code, configuration, database schema, migrations, and runtime evidence.

3. **Make the smallest correct change that solves the demonstrated problem.**

4. **Preserve existing invariants unless a deliberate architecture decision changes them.**

5. **Never invent an API, DTO field, database relationship, authorization rule, lifecycle rule, fallback behavior, or product behavior.**

6. **Never weaken backend validation to make the frontend work.**

7. **Never silently replace pinned/versioned data with "latest" data.**

8. **Security, privacy, authorization, and child/family isolation are product requirements, not polish.**

9. **Real verification means real delivery/validation.**
   A mock success is not email verification, phone verification, payment, consent, or any other real-world operation.

10. **Never claim compilation, runtime, database, browser, integration, or test verification unless it was actually performed.**

11. **Tests protect behavior.**
    Do not edit tests merely to make an implementation pass.

12. **Database migrations are deliberate, reviewable, and justified by actual schema requirements.**

13. **Accessibility and responsive behavior are acceptance criteria where applicable.**

14. **Motion must communicate state, hierarchy, feedback, orientation, or continuity—not decoration for its own sake.**

15. **Avoid premature complexity, duplicate abstractions, unnecessary dependencies, and speculative infrastructure.**

16. **Do not optimize engineering decisions for agent convenience.**
    The easiest implementation for Claude is not necessarily the correct implementation for Bookeyond Kid.

17. **Do not trade security, privacy, learning integrity, or correctness for implementation speed.**

---

# 4. REAL-PRODUCT STANDARD

Bookeyond Kid is being built toward a real, professionally usable product for real children, parents, teachers, principals, and administrators.

The goal is NOT merely:

- a prototype
- a technical demo
- a collection of working endpoints
- a visually impressive mockup
- an MVP that knowingly contains broken behavior

The goal is:

> **Build the smallest product scope that can be genuinely trusted and used in real life.**

"Professional" does not mean building every possible feature.

It means that features we choose to include are:

- functionally correct
- secure
- maintainable
- accessible
- understandable
- resilient
- honestly verified

A feature may be intentionally deferred.

A feature must not be intentionally fake, misleading, insecure, or knowingly broken.

---

# 5. DEFINITION OF DONE

Do not consider a feature complete merely because the code exists or compiles.

Depending on the feature, completion should include:

1. Correct product/domain behavior
2. Correct authorization and privacy boundaries
3. Correct database/state behavior
4. Correct error and failure handling
5. Correct frontend/backend contract
6. Accessibility and responsive behavior where applicable
7. No unnecessary architectural shortcuts
8. No fake or hardcoded production behavior
9. Appropriate automated verification
10. Runtime/browser verification when required
11. Clear evidence of what was actually verified
12. Appropriate handling of retries, interruptions, stale state, and lifecycle changes

A feature is only "done" at the verification level actually demonstrated.

---

# 6. VERIFICATION LEVELS

Use these labels precisely.

### INSPECTED

Code, configuration, database schema, migrations, or architecture was read and reviewed.

### COMPILED

The relevant project/module actually compiled successfully.

### TESTED

Relevant automated tests actually ran and passed.

### RUNTIME VERIFIED

The application actually started and the relevant behavior was exercised against running dependencies.

### BROWSER VERIFIED

The real user workflow was exercised through the actual frontend/browser.

### NOT VERIFIED

Required evidence is unavailable.

Never convert one verification level into another.

Examples:

- Code inspection is not compilation.
- Compilation is not testing.
- Testing is not runtime verification.
- Runtime verification is not browser verification.
- A successful API call is not proof that the complete user workflow works.

---

# 7. REAL-WORLD FAILURE RULE

Do not evaluate a feature only through its happy path.

For production-relevant capabilities, consider:

- invalid input
- unauthorized access
- expired state
- revoked state
- concurrent requests
- duplicate requests
- retries
- partial failure
- network failure
- stale frontend state
- version/lifecycle changes
- archived entities
- deleted entities
- interrupted workflows
- recovery/resume behavior
- historical data preservation
- transaction boundaries
- idempotency
- race conditions

A feature is not professionally complete merely because its normal path works.

When a failure mode can:

- corrupt learning state
- expose private data
- create unauthorized access
- lose important evidence
- produce incorrect mastery
- leave a child unable to continue
- leave a parent with misleading information
- create unrecoverable inconsistent state

treat it as a high-priority integrity issue.

---

# 8. NO SILENT DEGRADATION

Do not hide or silently ignore important failures.

Examples:

- Do not swallow authorization errors.
- Do not ignore conflict responses that leave a user stuck.
- Do not replace failed backend data with fake success data.
- Do not silently substitute a different learning version when the domain requires a pinned version.
- Do not silently invent fallback localization.
- Do not mark verification, consent, payment, mastery, or progress as successful without the underlying operation succeeding.
- Do not silently discard important backend errors.
- Do not silently convert an unsupported state into a successful state.

If the correct behavior is unknown:

> **Stop and report the product/domain decision required rather than inventing behavior.**

---

# 9. NO AGENT-CONVENIENCE ENGINEERING

Do not choose an implementation merely because it is easier for the agent to code.

Do not:

- weaken validation
- broaden permissions
- duplicate business logic unnecessarily
- hardcode temporary production behavior
- introduce fake defaults
- bypass existing domain boundaries
- delete existing data to avoid lifecycle complexity
- create speculative abstractions
- redesign unrelated architecture
- modify unrelated modules simply because they are nearby

If the correct implementation requires:

- a product decision
- a schema change
- a migration
- a new authorization relationship
- an architectural decision
- a lifecycle decision

surface that requirement explicitly.

Do not hide the decision inside implementation code.

---

# 10. CHILD DATA AND PRIVACY

Bookeyond Kid handles children's learning information.

Treat privacy and authorization boundaries as first-class architecture concerns.

Rules:

- Authentication does not imply authorization.
- A resource UUID is never proof of permission.
- Family ownership/access must be explicitly checked.
- School access must be explicitly authorized.
- Class membership and teacher assignment must be explicitly checked.
- School authorization, class enrollment, and teacher assignment are separate relationships.
- School access must not automatically expose family-private information.
- Historical learning evidence should not be casually deleted because an access relationship changes.
- Do not use privileged roles as a shortcut for ordinary authorization rules.
- Do not expose more personal or learning information than the user's role requires.

Never solve an authorization problem by simply allowing a broader role.

---

# 11. AUTHENTICATION AND IDENTITY

Bookeyond Kid must use real identity flows.

Email verification must eventually provide:

- real verification tokens
- expiry
- single-use behavior
- resend limits
- abuse protection
- secure state transitions

Phone verification must eventually provide:

- real OTP delivery
- expiry
- single-use behavior
- resend limits
- abuse protection
- secure state transitions

Do not fake verified states merely to make the UI work.

Authentication must account for:

- account status
- session state
- token expiration
- refresh-token rotation
- refresh-token reuse detection
- session-family revocation
- logout
- concurrent requests
- disabled/deleted accounts

Account state must remain authoritative on the server.

---

# 12. AUTHORIZATION PRINCIPLES

Authentication proves identity.

Authorization determines whether that identity may perform a specific action on a specific resource.

Never assume:

- valid JWT = permission
- role = permission to every resource
- UUID = ownership
- school membership = access to every child
- teacher role = access to every student
- principal role = unrestricted access to every school resource

Authorization must derive from actual domain relationships.

Where applicable, verify:

- account status
- role
- family relationship
- school relationship
- class relationship
- teacher assignment
- resource ownership
- resource lifecycle/status
- child status
- school/class status

---

# 13. LEARNING INTEGRITY

The learning system must be trustworthy.

Core loop:

**Content**
→ **Learning Path**
→ **Activity**
→ **Question**
→ **Answer**
→ **Evidence**
→ **Skill Mastery**
→ **Adaptive Decision / Review / Progression**
→ **Parent Insight**

Rules:

- Completion is not automatically mastery.
- Scores must be derived from the actual answer contract.
- Evidence must correspond to the actual learner response.
- Mastery must reflect real evidence.
- Adaptive decisions must use real learner state.
- Smart Review decisions must use real evidence/state.
- Parent insights must be grounded in actual learner evidence.
- Frontend resume behavior must respect backend attempt state.
- Never fabricate learning progress to make the UI look complete.
- Never make a learning decision from stale or unrelated learner state.
- Never allow one child's evidence/state to affect another child's learning state.

Any defect that can produce incorrect:

- scoring
- evidence
- mastery
- progression
- adaptive selection
- review scheduling
- parent insight

is a **learning-integrity defect** and should be treated as high priority.

---

# 14. CONTENT INTEGRITY

Content is versioned learning data and must be treated accordingly.

The system must clearly distinguish:

- draft content
- published content
- retired content

Rules:

- New learner assignments should use valid published content.
- Draft content must not leak into normal learner-facing flows.
- Exact pinned versions must be respected where the domain requires pinning.
- Do not silently replace an exact pinned version with the latest version.
- Version lifecycle must be explicit.
- Retiring a version must not accidentally corrupt existing learner paths.
- Historical evidence must remain correctly associated with the version that produced it.
- Learner responses must not expose authoring/moderation metadata unnecessarily.
- Correct answers must not be exposed through learner-safe DTOs.
- Ordering must be deterministic unless the domain explicitly defines controlled randomization.

Localization, media, and audio must have clear domain relationships.

Do not create fake language support by adding a selector that does not change actual content behavior.

Do not invent fallback localization semantics.

---

# 15. SCHOOL DOMAIN RULES

Do not collapse these relationships:

**Family → Child**

**School → Child Authorization**

**School Class → Child Enrollment**

**Teacher → Class Assignment**

They represent different permissions.

### School authorization

Answers:

> "Is this child legitimately authorized to participate in this school's educational domain?"

### Class enrollment

Answers:

> "Is this child associated with this particular class?"

### Teacher assignment

Answers:

> "Is this teacher authorized to operate within this class?"

These relationships must remain conceptually and technically distinct.

Do not invent school consent or authorization behavior.

If the schema/domain does not currently represent the required relationship:

> **Report the domain gap and stop before implementing a fake shortcut.**

Do not solve a missing school authorization model by simply allowing teachers or principals broader access.

---

# 16. CONTRACT INTEGRITY

Frontend and backend must agree on the actual API contract.

When a contract mismatch is discovered:

1. Trace the backend request/response.
2. Trace the frontend request/response.
3. Identify which side owns the authoritative semantics.
4. Identify whether the mismatch affects business meaning.
5. Fix the contract at the correct boundary.
6. Verify both sides.

Do not make the frontend "work" by transforming data in a way that changes domain meaning.

Do not make the backend accept ambiguous or incorrect client representations merely to accommodate an existing frontend bug.

Example:

If the backend expects an option ID but the frontend sends an option label, do not arbitrarily make the backend accept both unless that is the deliberate domain/API decision.

First determine the canonical contract.

---

# 17. FRONTEND PRODUCT RULES

The frontend is a real product surface, not merely an API wrapper.

The UI must:

- represent real backend state
- handle loading states
- handle empty states
- handle errors
- handle conflicts
- support recovery
- preserve user progress where appropriate
- respect backend lifecycle/state
- avoid fake success states

Do not silently swallow important API errors.

If the backend returns a conflict because an attempt already exists, the frontend must understand and recover from the actual state rather than simply ignoring the response.

If an attempt is resumable, the frontend must actually resume it correctly.

---

# 18. ACCESSIBILITY AND RESPONSIVE DESIGN

Accessibility and responsive behavior are product requirements.

Where applicable, include:

- readable typography
- sufficient contrast
- visible focus states
- keyboard accessibility
- touch-friendly targets
- semantic structure
- screen-reader support
- clear error communication
- accessible labels
- reduced-motion support
- responsive layouts

Do not treat accessibility as a later cosmetic pass.

---

# 19. CHILD EXPERIENCE

The child experience is a first-class product surface.

Bookeyond Kid should feel:

- warm
- playful
- premium
- curious
- safe
- welcoming
- modern
- visually memorable
- age-appropriate

It should NOT feel:

- babyish
- overly academic
- corporate
- generic
- hyperactive
- neon
- casino-like
- AI-generic

Graphics and animation are core parts of the experience, not optional decoration.

Use appropriately:

- custom illustrations
- original characters
- Learning Buddy
- visual storytelling
- animated feedback
- transitions
- micro-interactions
- animated learning progression
- audio-supported interaction
- touch-friendly interaction

Animation must serve learning, orientation, feedback, hierarchy, continuity, or delight.

Do not add motion simply because animation is possible.

Learning Buddy is an authored/product-controlled character.

It is NOT a conversational AI companion.

Do not introduce:

- leaderboards
- child-to-child comparison
- virtual currencies
- loss-aversion streaks
- engagement-maximizing notifications
- ads
- social/UGC
- screen-time optimization

unless explicitly approved as a deliberate product decision.

---

# 20. PARENT EXPERIENCE

Parent UX should be:

- clear
- calm
- trustworthy
- evidence-grounded
- actionable
- understandable

Parent information should answer questions such as:

- What is my child learning?
- What does the child appear to understand?
- What evidence supports that?
- What is improving?
- Where does the child need more practice?
- What should happen next?
- Why is the platform recommending this?

Do not present completion counts as though they prove mastery.

Do not present unsupported claims about a child's learning.

---

# 21. TEACHER EXPERIENCE

Teacher UX should be:

- efficient
- understandable
- practical
- useful for real teaching work

Teacher workflows must respect:

- school authorization
- class membership
- teacher assignment
- child privacy
- content ownership
- content lifecycle

Do not expose family-private information simply because a teacher works with the child.

---

# 22. ADMINISTRATION

Administrative capabilities should be explicit and auditable.

Do not use ADMIN privileges as a shortcut for ordinary application behavior.

Platform-level administration and school-level administration are different concepts.

Where appropriate, preserve:

- ownership
- audit history
- lifecycle state
- role boundaries
- school boundaries

---

# 23. PRODUCT QUALITY OVER FEATURE COUNT

Prefer:

**small scope + deep quality**

over:

**large scope + unreliable features**

Do not add features simply because they sound impressive.

Prioritize:

1. Learning correctness
2. Security and privacy
3. Core user workflows
4. Reliability
5. Accessibility
6. Product usability
7. Maintainability
8. Visual polish
9. Additional features

A deferred feature is acceptable.

A broken core feature is not.

---

# 24. PRODUCT QUALITY BAR

### Child UX

Warm, playful, premium, safe, age-appropriate, curious, memorable.

Never:

- babyish
- casino-like
- hyperactive
- AI-generic

### Parent UX

Clear, calm, evidence-grounded, trustworthy, actionable.

### Teacher UX

Efficient, understandable, useful for real teaching work.

### Admin UX

Controlled, understandable, auditable, operationally useful.

---

# 25. CURRENT BACKEND BASELINE

The following battles are accepted and must be preserved unless a new demonstrated defect requires changing them.

### Battle 2A — Learning Path / Activity Attempt Integrity

Accepted.

The system must preserve the invariant that an activity attempt remains correctly associated with its intended learning-path context.

### Battle 2B — Auto-Enrollment / Child Learning Initialization

Accepted.

Child initialization and enrollment behavior must preserve existing curriculum/content semantics and must not invent a default curriculum where the domain does not define one.

### Battle 2C — Evidence-Strength-Aware Parent Insight

Accepted.

Parent insight must remain grounded in sufficient evidence and must not falsely present weak evidence as established mastery.

### Battle 2D — Adaptive / Smart Review Transactional Reliability

Accepted.

Evidence persistence, mastery updates, idempotency, adaptive decisions, and Smart Review operations must preserve their transactional/idempotent integrity.

### Battle 3 — Content Read-Back / Localization / Audio Integrity

Accepted for its original scope.

The accepted work established learner-safe published content read-back and protected against draft QuestionVersions being served through published ActivityVersions.

A separate content-lifecycle defect remains open:

> A published ActivityVersion may pin a QuestionVersion that later becomes RETIRED. The learner read path must still be able to serve the exact pinned version without allowing draft content.

This lifecycle issue must be resolved in a dedicated content-lifecycle battle.

Do not silently reopen or redefine the accepted Battle 3 scope.

---

# 26. CURRENT SECURITY BASELINE

### Battle 4A — Refresh-Token Security

Implemented and accepted for the completed scope.

The refresh flow preserves session-family revocation behavior when reuse/race detection throws the expected invalid-session exception.

Current verification level:

**COMPILED**

Runtime and automated verification remain separate requirements unless actually performed.

### Battle 4B — System-Owned Content Authorization

Implemented and accepted for the completed scope.

System-owned content must not become teacher-writable merely because it has no owner.

Current verification level:

**COMPILED**

### Battle 4.1 — Build Integrity Recovery

Completed.

The `assignCurriculum` response-type compile defect was fixed using the existing learning enrollment response DTO.

Backend compilation succeeded.

### Battle 4C — School ↔ Child Authorization

**NOT IMPLEMENTED.**

This requires explicit product/domain decisions before implementation.

Do not invent the school authorization model.

---

# 27. KNOWN OPEN DOMAIN / INTEGRITY AREAS

These are examples of issues that must be handled deliberately rather than silently worked around.

Known areas include:

- school ↔ child authorization
- content lifecycle behavior for pinned retired versions
- authentication verification flows
- password reset/change
- rate limiting
- account-state enforcement
- learning answer/scoring integrity
- attempt resume behavior
- content localization behavior
- media/audio relationships
- school tenancy boundaries
- production observability
- full automated verification
- runtime/browser verification

The presence of an item here does not authorize implementing it immediately.

Use the current battle scope and product decisions to determine sequencing.

---

# 28. TESTING RULES

Tests are part of the product's engineering safety net.

Do not modify tests merely to make an implementation pass.

When a test fails:

1. Determine whether the implementation is wrong.
2. Determine whether the test represents the intended product behavior.
3. Determine whether the contract/domain has intentionally changed.
4. Only then modify the test if the intended behavior has actually changed.

For important learning/security workflows, prefer tests that verify real invariants rather than only superficial method behavior.

Eventually the product should have appropriate:

- unit tests
- integration tests
- authorization tests
- learning journey tests
- security tests
- persistence tests
- frontend tests where useful
- end-to-end tests for critical user workflows

---

# 29. DATABASE RULES

Database changes must be deliberate.

Before changing the schema:

1. Inspect existing migrations.
2. Inspect entity mappings.
3. Inspect foreign keys.
4. Inspect unique constraints.
5. Inspect indexes.
6. Inspect existing lifecycle/state semantics.
7. Determine whether the problem can be solved without schema change.
8. If schema change is required, make it explicit and reviewable.

Never add a migration merely because it is cleaner or more convenient.

Never silently alter existing data semantics.

Preserve historical learning evidence unless a deliberate product/data-retention decision says otherwise.

---

# 30. CONCURRENCY AND IDEMPOTENCY

Real users and networks produce retries and concurrent requests.

For operations that can be repeated or raced, consider:

- idempotency
- unique constraints
- transaction boundaries
- optimistic locking
- race conditions
- duplicate submissions
- retry behavior
- partial completion

Do not assume requests happen one at a time.

Do not add locking or complexity without understanding the actual domain and demonstrated race.

---

# 31. SCOPE DISCIPLINE

Before implementation, identify:

1. Concrete defect/capability
2. Affected domain boundary
3. Acceptance criteria
4. Relevant callers
5. Relevant database constraints
6. Verification method
7. Expected DB impact
8. Security/privacy impact

If a proposed change is a product decision rather than an integrity fix:

> **Stop and surface the decision.**

Do not silently turn an unresolved product decision into implementation behavior.

When a separate issue is discovered during a battle:

- verify it enough to establish whether it is real
- record it
- do not automatically expand the battle
- fix it only if required for the current acceptance criteria or explicitly authorized

---

# 32. CHANGE DISCIPLINE

For each implementation:

- inspect first
- understand callers
- understand persistence
- understand authorization
- make the smallest correct change
- re-read changed files
- inspect affected callers
- inspect relevant migrations/constraints
- compile when possible
- test when authorized/required
- verify runtime when required
- report what was not verified

Do not perform unrelated refactors during a focused battle.

Do not rename architecture merely for style.

Do not introduce a new abstraction when an existing one already correctly represents the domain.

---

# 33. AGENT REPORTING STANDARD

When completing an implementation battle, report:

**DONE**

What was actually changed.

**NOT DONE**

What was intentionally not changed.

**VERIFIED**

Exactly what was verified.

**NOT VERIFIED**

Exactly what could not be verified.

**RISKS**

Known remaining risks.

**DB IMPACT**

Migrations, schema, constraints, data changes, or "none."

**SECURITY IMPACT**

Authorization/privacy/security implications.

**ACCEPTANCE**

Each acceptance criterion marked explicitly.

**CHECKPOINT**

Provide a checkpoint artifact when the workflow requires one.

Do not spend excessive time writing a report when the remaining session/token budget should be used for implementation or verification.

---

# 34. SOURCE OF TRUTH

When multiple sources conflict, prioritize:

1. Actual runtime/database behavior
2. Current source code and configuration
3. Current migrations/schema
4. Current API contracts
5. Explicit current product decisions
6. Current engineering constitution
7. Older documentation
8. Previous agent claims
9. Assumptions

Do not preserve an old implementation merely because an earlier agent said it was correct.

Do not discard an existing behavior merely because it looks unusual.

Trace its purpose first.

---

# 35. FINAL PRINCIPLE

The project should always optimize for:

> **Correctness before convenience.**
>
> **Trust before feature count.**
>
> **Evidence before claims.**
>
> **Security before shortcuts.**
>
> **Real usability before demo appearance.**
>
> **Deliberate architecture before accidental architecture.**

Bookeyond Kid should never choose the easiest implementation when that implementation compromises:

- learning correctness
- child safety
- privacy
- security
- reliability
- maintainability
- accessibility
- real-world usability

When the correct behavior is known, implement it carefully.

When the correct behavior is unknown, do not invent it.

> **Stop, surface the decision, and resolve the domain question before coding.**