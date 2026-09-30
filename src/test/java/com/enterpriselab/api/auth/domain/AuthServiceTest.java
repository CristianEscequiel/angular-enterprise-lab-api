package com.enterpriselab.api.auth.domain;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * REQ-7 / REQ-8 / REQ-6: login válido devuelve token; usuario inexistente y
 * contraseña incorrecta lanzan la misma excepción con el mismo mensaje.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @Mock
    private UserRepository userRepository;
    @Mock
    private TokenIssuer tokenIssuer;

    private AuthService authService;
    private User admin;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, tokenIssuer);
        admin = new User(1L, "admin", passwordEncoder.encode("secret"), "Administrador",
                "admin@enterprise-lab.dev", Role.ADMINISTRADOR, null, null, null);
    }

    @Test
    void returnsTheTokenAndTheFullUserForValidCredentials() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(tokenIssuer.issueToken(admin)).thenReturn("jwt-token");

        AuthSession session = authService.login("admin", "secret");

        assertThat(session.token()).isEqualTo("jwt-token");
        assertThat(session.user()).isEqualTo(admin);
    }

    @Test
    void aTecnicoSessionCarriesItsProfile() {
        User tecnico = new User(4L, "tecnico", passwordEncoder.encode("secret"), "Técnico Mecánico de Guardia",
                "tecnico@enterprise-lab.dev", Role.TECNICO, "1001", "mecanico", "guardia");
        when(userRepository.findByUsername("tecnico")).thenReturn(Optional.of(tecnico));
        when(tokenIssuer.issueToken(tecnico)).thenReturn("jwt-token");

        AuthSession session = authService.login("tecnico", "secret");

        assertThat(session.user().legajo()).isEqualTo("1001");
        assertThat(session.user().specialty()).isEqualTo("mecanico");
        assertThat(session.user().teamType()).isEqualTo("guardia");
    }

    /** REQ-20, REQ-21: el usuario actual sale de la base en cada llamada, no de una copia. */
    @Test
    void currentUserReadsTheRepositoryOnEveryCall() {
        User before = new User(4L, "tecnico", "hash", "Técnico", "t@test.dev", Role.TECNICO, "1001",
                "mecanico", "guardia");
        User after = new User(4L, "tecnico", "hash", "Técnico", "t@test.dev", Role.TECNICO, "1001",
                "mecanico", "preventivo-correctivo");
        when(userRepository.findByUsername("tecnico")).thenReturn(Optional.of(before))
                .thenReturn(Optional.of(after));

        assertThat(authService.currentUser("tecnico").teamType()).isEqualTo("guardia");
        assertThat(authService.currentUser("tecnico").teamType()).isEqualTo("preventivo-correctivo");
    }

    /** REQ-24: un token válido de un usuario que ya no existe. */
    @Test
    void currentUserOfAnUnknownUsernameIsRejected() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.currentUser("ghost"))
                .isInstanceOf(UnknownSessionUserException.class);
        verifyNoInteractions(tokenIssuer);
    }

    @Test
    void unknownUserAndWrongPasswordFailIdentically() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        Throwable wrongPassword = catchLogin("admin", "wrong");
        Throwable unknownUser = catchLogin("ghost", "secret");

        assertThat(wrongPassword).isInstanceOf(InvalidCredentialsException.class);
        assertThat(unknownUser).isInstanceOf(InvalidCredentialsException.class);
        assertThat(unknownUser.getMessage()).isEqualTo(wrongPassword.getMessage());
        verifyNoInteractions(tokenIssuer);
    }

    @Test
    void unknownUserStillRunsAPasswordCheck() {
        PasswordEncoder spyEncoder = org.mockito.Mockito.spy(passwordEncoder);
        AuthService service = new AuthService(userRepository, spyEncoder, tokenIssuer);
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("ghost", "secret"))
                .isInstanceOf(InvalidCredentialsException.class);

        org.mockito.Mockito.verify(spyEncoder).matches(org.mockito.ArgumentMatchers.eq("secret"),
                org.mockito.ArgumentMatchers.anyString());
    }

    private Throwable catchLogin(String username, String password) {
        return org.assertj.core.api.Assertions.catchThrowable(() -> authService.login(username, password));
    }
}
