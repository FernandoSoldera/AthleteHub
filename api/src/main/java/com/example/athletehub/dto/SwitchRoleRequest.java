package com.example.athletehub.dto;

import com.example.athletehub.enums.Role;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body of POST /api/me/roles/switch — the role the user wants to be in. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SwitchRoleRequest {

    @NotNull
    private Role role;
}
