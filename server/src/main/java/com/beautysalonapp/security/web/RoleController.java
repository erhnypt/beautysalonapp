package com.beautysalonapp.security.web;

import com.beautysalonapp.security.application.UserService;
import com.beautysalonapp.security.domain.Permission;
import com.beautysalonapp.security.domain.Role;
import com.beautysalonapp.security.domain.RoleName;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * Rol → yetki eşleme yönetimi (§8.1). "Yönetici bu kümeleri daha sonra
 * ekranlardan özelleştirebilir (ADMIN hariç)" — {@link com.beautysalonapp.security.domain.RolePermissionCatalog}.
 */
@RestController
@RequestMapping("/api/v1/roles")
@PreAuthorize("hasAuthority('USER_VIEW')")
public class RoleController {

    private final UserService userService;

    public RoleController(UserService userService) {
        this.userService = userService;
    }

    public record RoleView(long id, RoleName name, String description, boolean systemRole,
                           Set<Permission> permissions) {
        static RoleView of(Role r) {
            return new RoleView(r.getId(), r.getName(), r.getDescription(), r.isSystemRole(), r.getPermissions());
        }
    }

    public record UpdatePermissionsRequest(@NotNull Set<Permission> permissions) {}

    @GetMapping
    public List<RoleView> list() {
        return userService.listRoles().stream().map(RoleView::of).toList();
    }

    @GetMapping("/permissions")
    public List<Permission> allPermissions() {
        return List.of(Permission.values());
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('USER_EDIT')")
    public RoleView updatePermissions(@PathVariable Long id, @RequestBody UpdatePermissionsRequest req) {
        return RoleView.of(userService.updateRolePermissions(id, req.permissions()));
    }

    @PostMapping("/{id}/permissions/reset")
    @PreAuthorize("hasAuthority('USER_EDIT')")
    public RoleView resetPermissions(@PathVariable Long id) {
        return RoleView.of(userService.resetRolePermissions(id));
    }
}
