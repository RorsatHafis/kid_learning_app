package com.platform.knowledge.exception;

import java.util.UUID;

/**
 * Adding this prerequisite edge would create a cycle in the skill graph (skill A
 * requiring skill B, where B already transitively requires A). Maps to HTTP 409 -
 * closes the gap V11's migration comment flagged: "acyclicity is NOT enforced at
 * the DB level... left as an application-level responsibility."
 */
public final class CyclicPrerequisiteException extends RuntimeException {

    public CyclicPrerequisiteException(UUID skillId, UUID prerequisiteSkillId) {
        super("Skill %s already transitively depends on %s; adding this prerequisite edge would create a cycle"
                .formatted(prerequisiteSkillId, skillId));
    }

}