package com.platform.common.web;

import com.platform.common.idempotency.exception.IdempotencyConflictException;
import com.platform.content.exception.IncompleteQuestionException;
import com.platform.content.validation.ContentValidationException;
import com.platform.learning.exception.InvalidAnswerException;
import com.platform.learning.exception.UnscorableAnswerException;
import com.platform.identity.exception.AccountLockedException;
import com.platform.identity.exception.CurrentPasswordMismatchException;
import com.platform.identity.exception.EmailAlreadyRegisteredException;
import com.platform.identity.exception.InvalidCredentialsException;
import com.platform.identity.exception.InvalidActionLinkException;
import com.platform.identity.exception.TooManyEmailsException;
import com.platform.identity.exception.WeakPasswordException;
import com.platform.security.session.InvalidSessionException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central point every error response passes through. Extending
 * ResponseEntityExceptionHandler means exceptions Spring MVC already knows how to
 * translate (malformed JSON, wrong HTTP method, missing params, etc.) come out in
 * the same ProblemDetail shape as the handlers below, via the single
 * createResponseEntity() override — one enrichment point, not one per exception type.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** M6: the plans that would lift a family's limit (absent in a slice without the subscription module). */
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.platform.subscription.service.PlanOffers> planOffers;

    static final String NOT_FOUND_DETAIL = "We couldn't find that. It may have been removed, or it isn't available to you.";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle("Validation Failed");

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {

            // If a field has multiple violations, the last one wins — an API error
            // response isn't the place for an exhaustive per-field violation list.
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());

        }
        problem.setProperty("errors", fieldErrors);

        return createResponseEntity(problem, headers, status, request);

    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle("Validation Failed");

        Map<String, String> violations = new LinkedHashMap<>();

        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {

            violations.put(violation.getPropertyPath().toString(), violation.getMessage());

        }
        problem.setProperty("errors", violations);

        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);

    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Object> handleIdempotencyConflict(IdempotencyConflictException ex, WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflicting Request");

        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.CONFLICT, request);

    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<Object> handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex, WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflicting Request");

        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.CONFLICT, request);

    }

    @ExceptionHandler(WeakPasswordException.class)
    public ResponseEntity<Object> handleWeakPassword(WeakPasswordException ex, WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Weak Password");

        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.UNPROCESSABLE_ENTITY, request);

    }

    @ExceptionHandler(CurrentPasswordMismatchException.class)
    public ResponseEntity<Object> handleCurrentPasswordMismatch(CurrentPasswordMismatchException ex, WebRequest request) {

        // 422 with a field error, like a validation failure on currentPassword - not 401:
        // the caller is authenticated; only the confirmation was wrong.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Incorrect Password");
        problem.setProperty("errors", java.util.Map.of("currentPassword", ex.getMessage()));

        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.UNPROCESSABLE_ENTITY, request);

    }

    @ExceptionHandler(InvalidAnswerException.class)
    public ResponseEntity<Object> handleInvalidAnswer(InvalidAnswerException ex, WebRequest request) {

        // Battle 5A: well-formed request, but the submitted value is not a valid response
        // to this question (unknown option id, non-boolean TRUE_FALSE, non-numeric NUMERIC).
        // 422 like WeakPasswordException - not 409 (nothing conflicts with resource state)
        // and not 400 (the body passed syntactic/bean validation). Nothing was persisted.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Invalid Answer");

        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.UNPROCESSABLE_ENTITY, request);

    }

    @ExceptionHandler(com.platform.content.media.MediaRejectedException.class)
    public ResponseEntity<Object> handleMediaRejected(com.platform.content.media.MediaRejectedException ex, WebRequest request) {

        // X11: the upload arrived, but its bytes aren't a picture or recording the platform serves
        // (or are too large for one). Written for the author; nothing was stored.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Media Not Accepted");

        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.UNPROCESSABLE_ENTITY, request);

    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            org.springframework.web.multipart.MaxUploadSizeExceededException ex, HttpHeaders headers,
            org.springframework.http.HttpStatusCode status, WebRequest request) {

        // X11: said in the author's terms, not the multipart limit's.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE,
                "A file can be at most 5 MB (a picture at most 2 MB).");
        problem.setTitle("Upload Too Large");

        return createResponseEntity(problem, headers, HttpStatus.CONTENT_TOO_LARGE, request);

    }

    @ExceptionHandler(UnscorableAnswerException.class)
    public ResponseEntity<Object> handleUnscorableAnswer(UnscorableAnswerException ex, WebRequest request) {

        // The 501 UnscorableAnswerException's own javadoc always documented, but that no
        // handler implemented (it fell through to handleUnexpected's 500).
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_IMPLEMENTED, ex.getMessage());
        problem.setTitle("Question Cannot Be Scored");

        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.NOT_IMPLEMENTED, request);

    }

    @ExceptionHandler(ContentValidationException.class)
    public ResponseEntity<Object> handleContentValidation(ContentValidationException ex, WebRequest request) {

        // Directive §19: publishing refused because the draft would fail a learner. Same
        // 409 as the completeness rules below, with the whole report so the Content
        // Studio can show every blocking issue at once.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "This draft can't be published yet: fix the listed issues first.");
        problem.setTitle("Not Ready To Publish");
        problem.setProperty("validation", ex.getReport());

        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.CONFLICT, request);

    }

    @ExceptionHandler(com.platform.subscription.service.PlanLimitException.class)
    public ResponseEntity<Object> handlePlanLimit(com.platform.subscription.service.PlanLimitException ex, WebRequest request) {

        // PLATFORM-EXPANSION.md §3: a family tried to add something its plan doesn't include.
        // 409 with the entitlement and its limit, so the parent is told plainly what their plan
        // allows instead of a generic failure.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Plan Limit Reached");
        problem.setProperty("entitlement", ex.getEntitlement().name());
        problem.setProperty("limit", ex.getLimit());
        // M6: what kind of plan, that nothing already there is lost, and the real plans that include more.
        problem.setProperty("reason", "PLAN_LIMIT");
        problem.setProperty("audience", ex.getEntitlement().audience().name());
        problem.setProperty("dataKept", true);
        problem.setProperty("unlockedBy", unlocking(ex));

        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.CONFLICT, request);

    }

    /** The offers never decide the refusal: if they can't be read, the refusal is still answered, without them. */
    private java.util.List<com.platform.subscription.service.PlanOffers.Offer> unlocking(com.platform.subscription.service.PlanLimitException ex) {
        try {
            com.platform.subscription.service.PlanOffers offers = planOffers == null ? null : planOffers.getIfAvailable();
            return offers == null ? java.util.List.of() : offers.unlocking(ex.getEntitlement(), ex.getLimit());
        } catch (RuntimeException e) {
            log.warn("Couldn't read the plans that lift {}: {}", ex.getEntitlement(), e.toString());
            return java.util.List.of();
        }
    }

    @ExceptionHandler(com.platform.security.stepup.StepUpRequiredException.class)
    public ResponseEntity<Object> handleStepUpRequired(com.platform.security.stepup.StepUpRequiredException ex, WebRequest request) {

        // M8: the action needs the password re-entered on this sign-in; the client asks for it and tries again.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Password Needed");
        problem.setProperty("stepUpRequired", true);
        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.FORBIDDEN, request);

    }

    @ExceptionHandler(com.platform.subscription.service.SchoolPaymentRequiredException.class)
    public ResponseEntity<Object> handleSchoolPaymentRequired(com.platform.subscription.service.SchoolPaymentRequiredException ex,
                                                              WebRequest request) {

        // Y44: school services need a paid subscription. 402 with the school, so its staff are shown the
        // school's billing state (and its principal how to pay) rather than a generic refusal.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYMENT_REQUIRED, ex.getMessage());
        problem.setTitle("School Payment Required");
        problem.setProperty("schoolPaymentRequired", true);
        problem.setProperty("schoolId", ex.getSchoolId());
        problem.setProperty("reason", ex.isExpired() ? "SUBSCRIPTION_EXPIRED" : "SCHOOL_UNPAID");
        problem.setProperty("audience", "SCHOOL");
        problem.setProperty("dataKept", true);

        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.PAYMENT_REQUIRED, request);

    }

    @ExceptionHandler(com.platform.child.service.DailyLimitReachedException.class)
    public ResponseEntity<Object> handleDailyLimit(com.platform.child.service.DailyLimitReachedException ex, WebRequest request) {

        // M3: today's learning time is used, so a new activity doesn't start. 409 with the day's figures, so the
        // child's space shows a calm close and the way back tomorrow (and the family how to give more time).
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Daily Learning Time Used");
        problem.setProperty("dailyLimitReached", true);
        problem.setProperty("childId", ex.getChildId());
        problem.setProperty("limitMinutes", ex.getToday().limitMinutes());
        problem.setProperty("usedMinutes", ex.getToday().usedMinutes());
        problem.setProperty("extraMinutes", ex.getToday().extraMinutes());

        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.CONFLICT, request);

    }

    @ExceptionHandler(com.platform.content.media.StorageLimitException.class)
    public ResponseEntity<Object> handleStorageLimit(com.platform.content.media.StorageLimitException ex, WebRequest request) {

        // Y35: an upload that doesn't fit the author's storage allowance. Nothing was stored; the
        // numbers let the studio show how full it is and what the file needed.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Storage Limit Reached");
        problem.setProperty("usedBytes", ex.getUsedBytes());
        problem.setProperty("limitBytes", ex.getLimitBytes());
        problem.setProperty("neededBytes", ex.getNeededBytes());

        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.CONFLICT, request);

    }

    @ExceptionHandler(IncompleteQuestionException.class)
    public ResponseEntity<Object> handleIncompleteQuestion(IncompleteQuestionException ex, WebRequest request) {

        // Publishing content that fails its completeness rules conflicts with the version's
        // current state - 409, the same status ActivityService.publish's equivalent rule
        // ("must contain at least one question", an IllegalStateException) already returns.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Incomplete Question");

        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.CONFLICT, request);

    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Object> handleInvalidCredentials(InvalidCredentialsException ex, WebRequest request) {
 
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Authentication Failed");
 
        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.UNAUTHORIZED, request);
 
    }

    @ExceptionHandler(InvalidSessionException.class)
    public ResponseEntity<Object> handleInvalidSession(InvalidSessionException ex, WebRequest request) {

        // Same generic 401 shape as InvalidCredentialsException, deliberately: a failed
        // refresh must read identically whether the token was unknown, time-expired,
        // already rotated (reuse), or already revoked - see InvalidSessionException's
        // javadoc. Never include the presented token anywhere in this response.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Authentication Failed");

        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.UNAUTHORIZED, request);

    }

    @ExceptionHandler(InvalidActionLinkException.class)
    public ResponseEntity<Object> handleInvalidActionLink(InvalidActionLinkException ex, WebRequest request) {

        // One shape for unknown, expired, used and replaced links: never reveal which.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Link Not Usable");

        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);

    }

    @ExceptionHandler(TooManyEmailsException.class)
    public ResponseEntity<Object> handleTooManyEmails(TooManyEmailsException ex, WebRequest request) {

        long seconds = Math.max(1, ex.retryAfter().toSeconds());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problem.setTitle("Too Many Requests");
        problem.setProperty("retryAfterSeconds", seconds);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(seconds));

        return createResponseEntity(problem, headers, HttpStatus.TOO_MANY_REQUESTS, request);

    }

    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<Object> handleTooManyAttempts(TooManyAttemptsException ex, WebRequest request) {

        long seconds = Math.max(1, ex.retryAfter().toSeconds());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problem.setTitle("Too Many Requests");
        problem.setProperty("retryAfterSeconds", seconds);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(seconds));

        return createResponseEntity(problem, headers, HttpStatus.TOO_MANY_REQUESTS, request);

    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<Object> handleAccountLocked(AccountLockedException ex, WebRequest request) {
 
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.LOCKED, ex.getMessage());
        problem.setTitle("Account Locked");
        problem.setProperty("lockedUntil", ex.getLockedUntil());
 
        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.LOCKED, request);
 
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException ex, WebRequest request) {

        log.warn("Data integrity violation", ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "The request conflicts with existing data");
        problem.setTitle("Conflicting Request");

        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.CONFLICT, request);

    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFound(ResourceNotFoundException ex, WebRequest request) {

        // Was previously unhandled here and falling through to handleUnexpected()
        // below (500) - every existing "orElseThrow(() -> new ResourceNotFoundException(...))"
        // across the codebase (ActivityAttemptService, EnrollmentService,
        // CurriculumService, etc.) was affected, not just the endpoints added this
        // session.
        // One answer for every "not found", whatever was asked for and whether it doesn't
        // exist or belongs to someone else: the specific message could name a resource the
        // caller never supplied (e.g. the child behind another family's enrollment) or tell
        // a foreign id from a missing one (CrossFamilyAccessSweepIntegrationTests). The
        // detail stays in the server log, under the request's correlation id.
        log.debug("Not found: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, NOT_FOUND_DETAIL);
        problem.setTitle("Not Found");

        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.NOT_FOUND, request);

    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "You do not have access to this resource");
        problem.setTitle("Forbidden");

        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.FORBIDDEN, request);

    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Object> handleIllegalState(IllegalStateException ex, WebRequest request) {

        // Same pre-existing gap as ResourceNotFoundException above: every entity's
        // own state-transition guard (ActivityAttempt.complete(), Enrollment.pause(),
        // Membership.remove(), ActivityAttemptService.submitAnswer()'s
        // not-IN_PROGRESS check, etc.) throws this and was falling through to 500.
        // 409 (not 400) because the request is well-formed - it conflicts with the
        // resource's current state.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflicting Request");

        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.CONFLICT, request);

    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {

        // Malformed-input guards (e.g. AuthController's role parsing) throw this and were
        // falling through to 500. 400, not 409/403: the request itself is invalid, it
        // doesn't conflict with resource state and isn't an access question. Entity
        // invariant checks (Assert.notNull/hasText) also throw this - those should only
        // ever fire on a genuine caller bug, so treating them as 400 here is still correct,
        // just rarer in practice.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid Request");

        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);

    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {

        // Never leak the exception message or stack trace to the client (Section 53).
        // Log it fully here, with the correlation id already in MDC, for our own debugging.
        log.error("Unhandled exception", ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setTitle("Internal Server Error");

        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);

    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

        if (body instanceof ProblemDetail problem) {

            // What a person reads is said in the request's language (en or km); logs keep the English.
            problem.setDetail(ServerText.forCurrentRequest(problem.getDetail(), statusCode.value()));
            if (problem.getProperties() != null && problem.getProperties().get("errors") instanceof Map<?, ?> errors) {
                Map<Object, Object> localized = new LinkedHashMap<>();
                errors.forEach((field, message) -> localized.put(field,
                        message instanceof String text ? ServerText.forCurrentRequest(text, statusCode.value()) : message));
                problem.setProperty("errors", localized);
            }
            problem.setProperty("timestamp", Instant.now());
            problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));

        }

        return super.createResponseEntity(body, headers, statusCode, request);

    }

}