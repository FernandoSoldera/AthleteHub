package com.example.athletehub.service;

import com.example.athletehub.dto.SignupRequest;
import com.example.athletehub.dto.UserDto;
import com.example.athletehub.enums.MessageCode;
import com.example.athletehub.enums.Role;
import com.example.athletehub.exception.ConflictException;
import com.example.athletehub.model.User;
import com.example.athletehub.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks AuthService authService;

    private SignupRequest validRequest() {
        return SignupRequest.builder()
                .email("Alex@Example.com")
                .password("supersecret1!")
                .fullName("Alex Carter")
                .handle("Alex.Lifts")
                .build();
    }

    @Test
    void register_normalizes_email_and_handle_hashes_password_and_grants_athlete_role() {
        when(userRepository.existsByEmail("alex@example.com")).thenReturn(false);
        when(userRepository.existsByHandle("alex.lifts")).thenReturn(false);
        when(passwordEncoder.encode("supersecret1!")).thenReturn("$2a$10$HASH");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(42L);
            return u;
        });

        UserDto dto = authService.register(validRequest());

        assertThat(dto.getId()).isEqualTo(42L);
        assertThat(dto.getEmail()).isEqualTo("alex@example.com");
        assertThat(dto.getHandle()).isEqualTo("alex.lifts");
        assertThat(dto.getFullName()).isEqualTo("Alex Carter");
        assertThat(dto.getRoles()).containsExactly(Role.ATHLETE);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("alex@example.com");
        assertThat(saved.getHandle()).isEqualTo("alex.lifts");
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$10$HASH");
        assertThat(saved.getRoles()).containsExactly(Role.ATHLETE);
    }

    @Test
    void register_rejects_duplicate_email() {
        when(userRepository.existsByEmail("alex@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(validRequest()))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", MessageCode.EMAIL_ALREADY_REGISTERED);

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_rejects_duplicate_handle() {
        when(userRepository.existsByEmail("alex@example.com")).thenReturn(false);
        when(userRepository.existsByHandle("alex.lifts")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(validRequest()))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", MessageCode.HANDLE_ALREADY_TAKEN);

        verify(userRepository, never()).save(any());
    }
}
