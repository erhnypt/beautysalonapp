package com.beautysalonapp.security.web;

import com.beautysalonapp.security.application.UserService;
import com.beautysalonapp.security.domain.AppUser;
import com.beautysalonapp.security.domain.RoleName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAuthority('USER_VIEW')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    public record UserView(Long id, String username, String fullName, boolean enabled,
                           boolean mustChangePassword, Set<RoleName> roles, Instant lastLoginAt) {
        static UserView of(AppUser u) {
            return new UserView(u.getId(), u.getUsername(), u.getFullName(), u.isEnabled(),
                    u.isMustChangePassword(),
                    u.getRoles().stream().map(r -> r.getName()).collect(java.util.stream.Collectors.toSet()),
                    u.getLastLoginAt());
        }
    }

    public record CreateUserRequest(@NotBlank String username, @NotBlank String fullName,
                                    @NotBlank String password, @NotEmpty Set<RoleName> roles) {
    }

    public record UpdateRolesRequest(@NotEmpty Set<RoleName> roles) {
    }

    public record ResetPasswordRequest(@NotBlank String newPassword) {
    }

    public record SetEnabledRequest(boolean enabled) {
    }

    @GetMapping
    public List<UserView> list() {
        return userService.list().stream().map(UserView::of).toList();
    }

    @GetMapping("/{id}")
    public UserView get(@PathVariable Long id) {
        return UserView.of(userService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_EDIT')")
    public UserView create(@Valid @RequestBody CreateUserRequest req) {
        return UserView.of(userService.create(req.username(), req.fullName(), req.password(), req.roles()));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('USER_EDIT')")
    public UserView updateRoles(@PathVariable Long id, @Valid @RequestBody UpdateRolesRequest req) {
        return UserView.of(userService.updateRoles(id, req.roles()));
    }

    @PutMapping("/{id}/enabled")
    @PreAuthorize("hasAuthority('USER_EDIT')")
    public UserView setEnabled(@PathVariable Long id, @RequestBody SetEnabledRequest req) {
        return UserView.of(userService.setEnabled(id, req.enabled()));
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasAuthority('USER_EDIT')")
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest req) {
        userService.resetPassword(id, req.newPassword());
    }
}
