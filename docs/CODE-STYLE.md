# Code style

Short rules for code that reads the same across the repository. Where a file already has a settled style, match the
surrounding code; never reformat unrelated code in a change.

## Every source file says what it is for

- **Java**: a Javadoc directly above the top-level type (above its annotations) saying what the class is
  responsible for and any rule it protects. One or two sentences is usually enough; say *why* where a reader would
  otherwise have to guess.
- **TypeScript / TSX / JS / CSS**: a block comment at the top of the file, or a JSDoc on the file's main export.
- **Tests**: what behaviour the file protects, in words a product owner would recognise.
- **Shell and Python scripts**: a `#` comment after the shebang saying what the script does and how to run it.
- **SQL migrations**: new migrations start with `--` lines saying what the migration changes and why.
  **Never edit a migration that has been applied**, not even a comment: Flyway checks each migration's checksum and
  will refuse to start against a database that ran the earlier text.

Not commented: JSON, lockfiles, generated files, vendored code, and the Maven wrapper.

Comments say what the code cannot: purpose, a business rule, a security or privacy reason, a decision and its
alternative. They never narrate the obvious line below them, and they never claim more than the code does
(for example "verified" for something that was only compiled).

## Java layout (as in `AssessmentService`)

```java
/** What this service is for, and the rule it protects. */
@Service
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;

    public AssessmentService(AssessmentRepository assessmentRepository) {

        this.assessmentRepository = assessmentRepository;

    }

    @Transactional
    public AssessmentVersion createDraftVersion(UUID assessmentId, String instructions) {

        if (!assessmentRepository.existsById(assessmentId)) {

            throw new ResourceNotFoundException("Assessment", assessmentId);

        }

        int next = ...;

        return assessmentVersionRepository.save(AssessmentVersion.draft(assessmentId, next, instructions));

    }

}
```

- A blank line after a method's (or block's) opening brace and before its closing brace.
- Blank lines between logical steps inside a method.
- `@Transactional` (or `@Transactional(readOnly = true)`) on the public service method that needs it.
- Constructor injection with `private final` fields.
- Lines up to about 130 characters.
- No trailing whitespace; blank lines are empty.

## Messages people see

- A message thrown to a person (`IllegalStateException`, `IllegalArgumentException`) is a full sentence and has a
  Khmer entry in `common/web/ServerText.java`; `ServerTextCoverageTests` fails otherwise. Startup configuration
  errors use `ConfigurationContract.require` instead.
- Frontend words live in `*.messages.ts` catalogs with English and Khmer; `src/i18n/hardcoded.test.ts` and
  `catalogs.test.ts` fail on text written into a screen or Khmer copied from English.

## TypeScript

- No formatter is configured: match the surrounding code, with lines up to about 130 characters. If you format with
  Prettier, use `--print-width 130` and only on the files you changed.
- `npm run lint` passes with no errors. Shared non-component helpers go in their own module, so React's fast
  refresh keeps working.
