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

public final class ChildDtos {

    private ChildDtos() {
    }

    public record CreateChildRequest(
            @NotBlank @Size(max = 80) String displayName,
            @NotNull @PastOrPresent LocalDate dateOfBirth
    ) {
    }

    public record UpdateChildRequest(
            @NotBlank @Size(max = 80) String displayName
    ) {
    }

    public record ChildResponse(
            UUID id,
            String displayName,
            LocalDate dateOfBirth,
            ChildStatus status,
            /** When a DELETION_PENDING child will be erased; null otherwise. */
            Instant eraseAfter
    ) {
        public static ChildResponse from(Child child) {
            return new ChildResponse(child.getId(), child.getDisplayName(), child.getDateOfBirth(), child.getStatus(),
                    child.getEraseAfter());
        }
    }

}
