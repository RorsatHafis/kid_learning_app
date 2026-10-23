package com.platform.security.stepup;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.identity.entity.Account;
import com.platform.identity.service.PasswordConfirmation;
import com.platform.security.jwt.JwtService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * M8: step-up authentication. An account re-enters its password and gets a short proof, bound to this sign-in, that
 * actions marked {@link RequiresStepUp} require. A wrong password counts towards the account's lockout, like signing in.
 */
@Service
public class StepUpService {

    /** How long one re-entry of the password lasts. */
    public static final Duration WINDOW = Duration.ofMinutes(5);

    private final PasswordConfirmation passwords;
    private final JwtService jwt;
    private final AuditService audit;

    public StepUpService(PasswordConfirmation passwords, JwtService jwt, AuditService audit) {
        this.passwords = passwords;
        this.jwt = jwt;
        this.audit = audit;
    }

    @Transactional(noRollbackFor = com.platform.identity.exception.CurrentPasswordMismatchException.class)
    public JwtService.IssuedToken stepUp(UUID accountId, UUID sessionFamilyId, String password) {
        if (sessionFamilyId == null) {
            throw new StepUpRequiredException();
        }
        Account account = passwords.confirm(accountId, password);
        audit.record(new RecordAuditEvent(null, accountId, AuditActorType.valueOf(account.getPlatformRole().name()),
                "ACCOUNT_STEPPED_UP", "ACCOUNT", accountId, Map.of("minutes", WINDOW.toMinutes())));
        return jwt.issueStepUp(accountId, sessionFamilyId, WINDOW);
    }

    public void require(UUID accountId, UUID sessionFamilyId, String token) {
        if (!jwt.isValidStepUp(token, accountId, sessionFamilyId)) {
            throw new StepUpRequiredException();
        }
    }

}
