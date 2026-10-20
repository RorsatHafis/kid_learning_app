package com.platform.school.repository;

import com.platform.school.entity.SchoolStaff;
import com.platform.school.entity.SchoolStaffStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for school staff (teachers invited by a principal): a school's staff, an account's schools, and a
 * locked read for accepting or removing.
 */
public interface SchoolStaffRepository extends JpaRepository<SchoolStaff, UUID> {

    List<SchoolStaff> findBySchoolIdOrderByInvitedAtDesc(UUID schoolId);

    List<SchoolStaff> findByAccountIdAndStatus(UUID accountId, SchoolStaffStatus status);

    Optional<SchoolStaff> findBySchoolIdAndAccountId(UUID schoolId, UUID accountId);

    boolean existsBySchoolIdAndAccountIdAndStatus(UUID schoolId, UUID accountId, SchoolStaffStatus status);

    long countBySchoolIdAndStatusIn(UUID schoolId, java.util.Collection<SchoolStaffStatus> statuses);

    /** Whether {@code accountId} is ACTIVE staff at a school {@code principalId} is the principal of. */
    @Query("select count(s) > 0 from SchoolStaff s, School sc where s.schoolId = sc.id and sc.principalAccountId = :principalId"
            + " and s.accountId = :accountId and s.status = com.platform.school.entity.SchoolStaffStatus.ACTIVE")
    boolean isActiveStaffOfPrincipal(@Param("accountId") UUID accountId, @Param("principalId") UUID principalId);

    /** Row-locked: an invitation answered twice at once, or removed while being accepted, resolves one way. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SchoolStaff s where s.id = :id")
    Optional<SchoolStaff> findForUpdate(@Param("id") UUID id);

}
