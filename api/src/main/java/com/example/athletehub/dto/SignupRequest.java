package com.example.athletehub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignupRequest {

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;

    @NotBlank
    @Size(min = 1, max = 80)
    private String fullName;

    /** Public handle: lowercase letters, digits, dot and underscore. */
    @NotBlank
    @Size(min = 3, max = 40)
    @Pattern(regexp = "^[a-zA-Z0-9_.]+$",
             message = "handle may only contain letters, digits, dot and underscore")
    private String handle;

    /**
     * Biological sex — used by AH-041 body-fat formulas. Optional at signup;
     * users can add it later via PATCH /me. The schema CHECK enforces the
     * value set; the @Pattern here keeps the response code at 400
     * VALIDATION_FAILED instead of 500 DataIntegrityViolation.
     */
    @Pattern(regexp = "^(male|female)$",
             message = "sex must be 'male' or 'female'")
    private String sex;
}
