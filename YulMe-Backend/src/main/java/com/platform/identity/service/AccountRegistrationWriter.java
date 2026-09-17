package com.platform.identity.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.common.idempotency.service.IdempotencyService;
import com.platform.family.entity.Family;
import com.platform.family.entity.Membership;
import com.platform.family.entity.MembershipRole;
import com.platform.family.repository.FamilyRepository;
import com.platform.family.repository.MembershipRepository;
import com.platform.identity.dto.RegisterAccountCommand;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.exception.EmailAlreadyRegisteredException;
import com.platform.identity.repository.AccountRepository;

@Service
class AccountRegistrationWriter  {

    private final AccountRepository accountRepository;
    private final FamilyRepository familyRepository;
    private final MembershipRepository membershipRepository;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
 
    AccountRegistrationWriter(AccountRepository accountRepository, FamilyRepository familyRepository,
                               MembershipRepository membershipRepository, IdempotencyService idempotencyService,
                               AuditService auditService, PasswordEncoder passwordEncoder,
                               PasswordPolicy passwordPolicy) {
                                
        this.accountRepository = accountRepository;
        this.familyRepository = familyRepository;
        this.membershipRepository = membershipRepository;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;

    }

    @Transactional
    Account write(RegisterAccountCommand command, String normalizedEmail, UUID guardId) {
        passwordPolicy.validate(command.rawPassword());
 
        if (accountRepository.findByNormalizedEmail(normalizedEmail).isPresent()) {

            throw new EmailAlreadyRegisteredException();

        }

        PlatformRole role = command.resolvedPlatformRole();
 
        Account account = accountRepository.saveAndFlush(
                Account.register(command.email(), passwordEncoder.encode(command.rawPassword()), role));
        if (role == PlatformRole.PRINCIPAL) {
            account.markPendingApproval();
        }

        // Family membership is a PARENT-only concept (see PlatformRole's javadoc) -
        // TEACHER/PRINCIPAL/ADMIN accounts are scoped through the school domain
        // instead (SchoolClass/TeacherAssignment) and never own or belong to a family.
        Map<String, Object> auditDetails;
        if (role == PlatformRole.PARENT) {

            Family family = familyRepository.save(Family.create(command.resolvedFamilyName()));
            membershipRepository.save(Membership.create(family.getId(), account.getId(), MembershipRole.OWNER));
            auditDetails = Map.of("familyId", family.getId().toString(), "platformRole", role.name());

        } else {

            auditDetails = Map.of("platformRole", role.name());

        }
 
        auditService.record(new RecordAuditEvent(
                null, account.getId(), actorTypeFor(role), "ACCOUNT_REGISTERED", "ACCOUNT",
                account.getId(), auditDetails));
 

        idempotencyService.complete(guardId);
 
        return account;

    }

    private static AuditActorType actorTypeFor(PlatformRole role) {
        return switch (role) {
            case PARENT -> AuditActorType.PARENT;
            case TEACHER -> AuditActorType.TEACHER;
            case PRINCIPAL -> AuditActorType.PRINCIPAL;
            case ADMIN -> AuditActorType.ADMIN;
        };
    }
    
}
