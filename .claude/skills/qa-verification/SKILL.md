# QA and Verification
Verification ladder:
1. code inspection
2. static/type checks
3. compile/build
4. targeted tests
5. application startup
6. database/runtime API exercise
7. browser/user-flow verification

Report each separately. A failed environment dependency is not a passing verification. Add targeted regression tests when they protect a demonstrated invariant; never rewrite tests to hide defects.
