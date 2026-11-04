package com.platform.child;

import com.platform.child.entity.Child;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A child's date of birth is a real one when the child is added, as when it is corrected: not in the future, and not a
 * mistyped year ("0019" for "2019") - the age decides which paths fit the child.
 */
class ChildBirthDateTests {

    private static final UUID FAMILY = UUID.randomUUID();

    @Test
    void a_mistyped_year_is_refused_when_the_child_is_added_as_when_it_is_corrected() {
        assertThatThrownBy(() -> Child.enroll(FAMILY, "Sokha", LocalDate.of(19, 3, 14)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Enter the child's real date of birth");
        assertThatThrownBy(() -> Child.enroll(FAMILY, "Sokha", LocalDate.of(1900, 1, 1)))
                .hasMessage("Enter the child's real date of birth");

        Child child = Child.enroll(FAMILY, "Sokha", LocalDate.of(2019, 3, 14));
        assertThatThrownBy(() -> child.correctDateOfBirth(LocalDate.of(19, 3, 14)))
                .hasMessage("Enter the child's real date of birth");
    }

    @Test
    void a_real_date_of_birth_is_kept() {
        assertThat(Child.enroll(FAMILY, "Sokha", LocalDate.of(2019, 3, 14)).getDateOfBirth()).isEqualTo(LocalDate.of(2019, 3, 14));
        assertThat(Child.enroll(FAMILY, "Old record", LocalDate.of(1900, 1, 2)).getDateOfBirth()).isEqualTo(LocalDate.of(1900, 1, 2));
        assertThat(Child.enroll(FAMILY, "Born today", LocalDate.now()).getDateOfBirth()).isEqualTo(LocalDate.now());
    }
}
