package com.platform.admin.web;

import com.platform.admin.service.AdminService;
import com.platform.identity.entity.Account;
import jakarta.validation.Valid;
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
            @Valid @RequestBody AdminDtos.CreateStaffRequest request) {
        Account account = service.createStaffAccount(adminId, request.email(), request.password(), request.role());
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
    public List<AdminDtos.StaffAccountResponse> listStaff(@AuthenticationPrincipal UUID adminId) {
        return service.listStaff(adminId).stream().map(AdminDtos.StaffAccountResponse::of).toList();
    }
}
