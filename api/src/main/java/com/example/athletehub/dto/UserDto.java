package com.example.athletehub.dto;

import com.example.athletehub.enums.Role;
import com.example.athletehub.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/** Public view of a User. Never includes the password hash. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {

    private Long id;
    private String email;
    private String fullName;
    private String handle;
    private Integer avatarHue;
    private String bio;
    private Integer age;
    private BigDecimal heightCm;
    /** Biological sex ({@code "male"} / {@code "female"}); nullable. */
    private String sex;
    private LocalDate dateJoined;
    private Set<Role> roles;

    public static UserDto from(User user) {
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .handle(user.getHandle())
                .avatarHue(user.getAvatarHue())
                .bio(user.getBio())
                .age(user.getAge())
                .heightCm(user.getHeightCm())
                .sex(user.getSex())
                .dateJoined(user.getDateJoined())
                .roles(user.getRoles())
                .build();
    }
}
