package com.platform.child.web;

import java.time.Instant;
import com.platform.child.entity.Child;
import com.platform.child.entity.ChildStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Requests and responses for children. The response is what a parent sees; it carries no family-private data
 * beyond the child's own profile.
 */
public final class ChildDtos {

    private ChildDtos() {
    }

    /**
     * {@code familyChoosesSubjects}: true when the family will choose the child's subjects itself
     * (the parent's setup flow does, then enrols each one it chose). The child is then created with
     * no learning path, rather than started on the single curriculum for its age (Battle 2B), which
     * remains what happens when the field is absent or false.
     */
    public record CreateChildRequest(
            @NotBlank @Size(max = 80) String displayName,
            @NotNull @PastOrPresent LocalDate dateOfBirth,
            Boolean familyChoosesSubjects
    ) {
        public boolean startsWithFamilyChoice() {
            return Boolean.TRUE.equals(familyChoosesSubjects);
        }
    }

    /**
     * Either or both: a new name, and a corrected date of birth (Y12: a mistyped date set the
     * child's age and so the curricula offered first; the family can put it right).
     */
    public record UpdateChildRequest(
            @Size(max = 80) @jakarta.validation.constraints.Pattern(regexp = "(?s).*\\S.*", message = "must not be blank")
            String displayName,
            @PastOrPresent LocalDate dateOfBirth
    ) {
    }

    /** The language of the child's own space: en or km. */
    public record ChildLanguageChange(
            @NotBlank @jakarta.validation.constraints.Pattern(regexp = com.platform.identity.UiLanguage.PATTERN) String language
    ) {
    }

    /** The Learning Buddy a child chose: OTTER, ELEPHANT, BUFFALO, GIBBON or FROG. */
    public record ChildBuddyChange(@jakarta.validation.constraints.NotNull com.platform.child.entity.LearningBuddy buddy) {
    }

    public record ChildResponse(
            UUID id,
            String displayName,
            LocalDate dateOfBirth,
            ChildStatus status,
            /** When a DELETION_PENDING child will be erased; null otherwise. */
            Instant eraseAfter,
            /** The language the child's own space is shown in (en or km). */
            String preferredLanguage,
            /** The Learning Buddy the child chose. */
            com.platform.child.entity.LearningBuddy learningBuddy
    ) {
        public static ChildResponse from(Child child) {
            return new ChildResponse(child.getId(), child.getDisplayName(), child.getDateOfBirth(), child.getStatus(),
                    child.getEraseAfter(), child.getPreferredLanguage(), child.getLearningBuddy());
        }
    }

}
