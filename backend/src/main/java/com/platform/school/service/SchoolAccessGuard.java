package com.platform.school.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.identity.entity.*;
import com.platform.identity.repository.AccountRepository;
import com.platform.school.entity.*;
import com.platform.school.repository.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.util.UUID;

/** Central server-side authorization for school-scoped requests. */
@Service
public class SchoolAccessGuard {
    private final AccountRepository accounts; private final SchoolRepository schools; private final SchoolClassRepository classes; private final TeacherAssignmentRepository assignments;
    public SchoolAccessGuard(AccountRepository accounts, SchoolRepository schools, SchoolClassRepository classes, TeacherAssignmentRepository assignments) { this.accounts=accounts; this.schools=schools; this.classes=classes; this.assignments=assignments; }
    public Account requireStaff(UUID accountId) { Account account=accounts.findById(accountId).orElseThrow(()->new ResourceNotFoundException("Account",accountId)); if(account.getPlatformRole()==PlatformRole.PARENT) throw new AccessDeniedException("Parent accounts cannot access school resources"); return account; }
    public School requireSchoolManage(UUID accountId, UUID schoolId) { Account account=requireStaff(accountId); School school=schools.findById(schoolId).orElseThrow(()->new ResourceNotFoundException("School",schoolId)); if(account.getPlatformRole()!=PlatformRole.ADMIN && !accountId.equals(school.getPrincipalAccountId())) throw new AccessDeniedException("Account is not allowed to manage this school"); return school; }
    public SchoolClass requireClassAccess(UUID accountId, UUID classId) { Account account=requireStaff(accountId); SchoolClass schoolClass=classes.findById(classId).orElseThrow(()->new ResourceNotFoundException("SchoolClass",classId)); if(account.getPlatformRole()==PlatformRole.ADMIN) return schoolClass; if(account.getPlatformRole()==PlatformRole.PRINCIPAL) { requireSchoolManage(accountId, schoolClass.getSchoolId()); return schoolClass; } if(!assignments.existsBySchoolClassIdAndTeacherAccountIdAndStatus(classId,accountId,TeacherAssignmentStatus.ACTIVE)) throw new AccessDeniedException("Teacher is not assigned to this class"); return schoolClass; }
}
