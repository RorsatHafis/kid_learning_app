package com.platform.admin.web;

import com.platform.admin.service.AdminService;
import com.platform.common.web.PageResponse;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminDtos.StaffAccountResponse createStaff(
            @AuthenticationPrincipal UUID adminId,
            @Valid @RequestBody AdminDtos.CreateStaffRequest request,
            jakarta.servlet.http.HttpServletRequest http) {
        Account account = service.createStaffAccount(adminId, request.email(), request.role(), http.getRemoteAddr());
        return AdminDtos.StaffAccountResponse.of(account);
    }

    @GetMapping("/principals/pending")
    public List<AdminDtos.StaffAccountResponse> listPendingPrincipals(@AuthenticationPrincipal UUID adminId) {
        return service.listPendingPrincipals(adminId).stream().map(AdminDtos.StaffAccountResponse::of).toList();
    }

    @PostMapping("/principals/{principalId}/approve")
    public AdminDtos.StaffAccountResponse approvePrincipal(@AuthenticationPrincipal UUID adminId, @PathVariable UUID principalId) {
        return AdminDtos.StaffAccountResponse.of(service.approvePrincipal(adminId, principalId));
    }

    @GetMapping("/staff")
    public PageResponse<AdminDtos.StaffAccountResponse> listStaff(@AuthenticationPrincipal UUID adminId,
                                                                  @RequestParam(required = false) @Size(max = 320) String q,
                                                                  @RequestParam(required = false) PlatformRole role,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "25") int size) {
        return service.listStaff(adminId, q, role, new PageResponse.Request(page, size)).map(AdminDtos.StaffAccountResponse::of);
    }
}
