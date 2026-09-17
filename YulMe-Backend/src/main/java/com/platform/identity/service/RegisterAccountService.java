package com.platform.identity.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.platform.common.idempotency.service.IdempotencyOutcome;
import com.platform.common.idempotency.service.IdempotencyService;
import com.platform.common.idempotency.service.RequestFingerprint;
import com.platform.identity.dto.RegisterAccountCommand;
import com.platform.identity.entity.Account;
import com.platform.identity.exception.EmailAlreadyRegisteredException;
import com.platform.identity.exception.WeakPasswordException;
import com.platform.identity.repository.AccountRepository;

@Service
public class RegisterAccountService {

    private static final String OPERATION = "account-registration";
    private static final Duration IDEMPOTENCY_VALIDITY = Duration.ofHours(24);
 
    private final AccountRepository accountRepository;
    private final IdempotencyService idempotencyService;
    private final AccountRegistrationWriter writer;
 
    public RegisterAccountService(AccountRepository accountRepository, IdempotencyService idempotencyService,
                                   AccountRegistrationWriter writer) {

        this.accountRepository = accountRepository;
        this.idempotencyService = idempotencyService;
        this.writer = writer;
        
    }

    public Account register(RegisterAccountCommand command) {
        
        String normalizedEmail = Account.normalize(command.email());
        String fingerprint = RequestFingerprint.sha256Hex(
                normalizedEmail + '\u0000' + command.resolvedFamilyName() + '\u0000' + command.rawPassword()
                        + '\u0000' + command.resolvedPlatformRole());
 
        IdempotencyOutcome outcome = idempotencyService.begin(
                anonymousActorId(normalizedEmail), OPERATION, command.idempotencyKey(), fingerprint,
                IDEMPOTENCY_VALIDITY);
 
        if (outcome instanceof IdempotencyOutcome.AlreadyCompleted) {

            return accountRepository.findByNormalizedEmail(normalizedEmail).orElseThrow(() -> new IllegalStateException(
                    "Registration for this email was marked complete but no matching account exists"));

        }
 
        UUID guardId = ((IdempotencyOutcome.Started) outcome).record().getId();
 
        try {

            return writer.write(command, normalizedEmail, guardId);

        } catch (EmailAlreadyRegisteredException | WeakPasswordException deterministicFailure) {

            // Retrying with the exact same input will fail identically every time, so cache
            // the failure for a fast, consistent rejection on retry rather than reprocessing.
            idempotencyService.fail(guardId);
            throw deterministicFailure;

        }

    }

    private static UUID anonymousActorId(String normalizedEmail) {

        return UUID.nameUUIDFromBytes(normalizedEmail.getBytes(StandardCharsets.UTF_8));

    }
    
}
