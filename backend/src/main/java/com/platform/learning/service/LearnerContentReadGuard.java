package com.platform.learning.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.service.ContentAccessGuard;
import com.platform.family.service.FamilyAccessGuard;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.learning.repository.ActivityAttemptRepository;
import com.platform.learning.repository.LearningPathItemRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Who may read a published activity's learner content (TD-13). Answer keys are never in it,
 * but a teacher's own activities are still theirs: only the children who may play them, and
 * the staff who may see them, read them.
 *
 * <ul>
 *   <li>A parent reads it for one of their own children ({@code childId}), when that child may
 *       play it - platform content, or content whose owner's curriculum the child follows
 *       (the free-practice rule) - or already has it on a path or in an attempt.</li>
 *   <li>Staff read platform content, their own, and (administrator, or a principal over their
 *       own school's active staff) what {@link ContentAccessGuard} lets them manage.</li>
 * </ul>
 * Anything else answers 404, as if the version did not exist.
 */
@Component
public class LearnerContentReadGuard {

    private final AccountRepository accounts;
    private final FamilyAccessGuard familyGuard;
    private final com.platform.family.service.ChildAccess childAccess;
    private final PathContentScope scope;
    private final LearningPathItemRepository pathItems;
    private final ActivityAttemptRepository attempts;
    private final ContentAccessGuard contentGuard;

    public LearnerContentReadGuard(AccountRepository accounts, FamilyAccessGuard familyGuard, com.platform.family.service.ChildAccess childAccess, PathContentScope scope,
                                   LearningPathItemRepository pathItems, ActivityAttemptRepository attempts,
                                   ContentAccessGuard contentGuard) {
        this.accounts = accounts;
        this.familyGuard = familyGuard;
        this.childAccess = childAccess;
        this.scope = scope;
        this.pathItems = pathItems;
        this.attempts = attempts;
        this.contentGuard = contentGuard;
    }

    @Transactional(readOnly = true)
    public void requireReadable(UUID accountId, UUID childId, ActivityVersion version) {
        Account account = accounts.findById(accountId).orElseThrow(() -> notFound(version));
        if (account.getPlatformRole() == PlatformRole.PARENT) {
            if (childId == null) {
                throw notFound(version);
            }
            childAccess.requireLearner(accountId, childId);
            boolean playable = scope.admitsActivityForChild(childId, version.getActivityId())
                    || pathItems.childHasVersionOnAPath(childId, version.getId())
                    || attempts.existsByChildIdAndActivityVersionId(childId, version.getId());
            if (!playable) {
                throw notFound(version);
            }
            return;
        }
        Optional<UUID> owner = scope.activityOwner(version.getActivityId());
        if (owner.isEmpty() || owner.get().equals(accountId)) {
            return;
        }
        try {
            contentGuard.requireOwnedWrite(accountId, owner.get());
        } catch (AccessDeniedException | ResourceNotFoundException e) {
            throw notFound(version);
        }
    }

    private static ResourceNotFoundException notFound(ActivityVersion version) {
        return new ResourceNotFoundException("ActivityVersion", version.getId());
    }

}
