package com.platform.identity.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.platform.identity.dto.LoginCommand;
import com.platform.identity.dto.LoginResult;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.AccountStatus;
import com.platform.identity.exception.AccountLockedException;
import com.platform.identity.exception.InvalidCredentialsException;
import com.platform.identity.repository.AccountRepository;
import com.platform.security.SecurityProperties;
import com.platform.security.jwt.JwtService;

@Service
public class LoginService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecurityProperties securityProperties;
    private final String dummyPasswordHash;
 
    public LoginService(AccountRepository accountRepository, PasswordEncoder passwordEncoder,
                         JwtService jwtService, SecurityProperties securityProperties) {

        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.securityProperties = securityProperties;

        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());

    }

    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class})
    public LoginResult login(LoginCommand command) {

        String normalizedEmail = Account.normalize(command.email());
        Optional<Account> found = accountRepository.findByNormalizedEmail(normalizedEmail);
 
        if (found.isEmpty()) {

            passwordEncoder.matches(command.rawPassword(), dummyPasswordHash);
            throw new InvalidCredentialsException();
            
        }
 
        Account account = found.get();
        Instant now = Instant.now();
 
        if (account.isLocked(now)) {

            throw new AccountLockedException(account.getLockedUntil());
            
        }
 
        if (!passwordEncoder.matches(command.rawPassword(), account.getPasswordHash())) {

            account.recordFailedLogin(
                    securityProperties.lockout().maxAttempts(), securityProperties.lockout().duration(), now);

            throw new InvalidCredentialsException();

        }
 
        if (account.getStatus() != AccountStatus.ACTIVE) {

            throw new InvalidCredentialsException();

        }
 
        account.resetFailedLogins();
 
        JwtService.IssuedToken issued = jwtService.issueAccessToken(account.getId(), account.getPlatformRole());
        return new LoginResult(account.getId(), account.getPlatformRole(), issued.token(), issued.expiresAt());

    }
    
}
