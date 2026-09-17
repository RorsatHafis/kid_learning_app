package com.platform.child.web;

import com.platform.child.entity.Child;
import com.platform.child.entity.ChildStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;
import java.util.UUID;

public final class ChildDtos {

    private ChildDtos() {
    }

    public record CreateChildRequest(
            @NotBlank String displayName,
            @NotNull @PastOrPresent LocalDate dateOfBirth
    ) {
    }

    public record ChildResponse(
            UUID id,
            String displayName,
            LocalDate dateOfBirth,
            ChildStatus status
    ) {
        public static ChildResponse from(Child child) {
            return new ChildResponse(child.getId(), child.getDisplayName(), child.getDateOfBirth(), child.getStatus());
        }
    }

}
