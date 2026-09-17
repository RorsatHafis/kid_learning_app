package com.platform.common.web;

import com.platform.common.idempotency.exception.IdempotencyConflictException;
import com.platform.identity.exception.AccountLockedException;
import com.platform.identity.exception.EmailAlreadyRegisteredException;
import com.platform.identity.exception.InvalidCredentialsException;
import com.platform.identity.exception.WeakPasswordException;
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

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Object> handleInvalidCredentials(InvalidCredentialsException ex, WebRequest request) {
 
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Authentication Failed");
 
        return createResponseEntity(problem, new HttpHeaders(), HttpStatus.UNAUTHORIZED, request);
 
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
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
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

            problem.setProperty("timestamp", Instant.now());
            problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));

        }

        return super.createResponseEntity(body, headers, statusCode, request);

    }

}