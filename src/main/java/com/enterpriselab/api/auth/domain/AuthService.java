package com.enterpriselab.api.auth.domain;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * REQ-7 / REQ-8: valida credenciales y emite el token de acceso. Usuario
 * inexistente y contraseña incorrecta lanzan la misma
 * {@link InvalidCredentialsException}, con el mismo mensaje, y tardan
 * parecido: sin usuario igual se corre {@code matches} contra un hash
 * dummy, para no filtrar qué usernames existen ni por tiempos.
 */
@Service
public class AuthService {

    static final String INVALID_CREDENTIALS_MESSAGE = "Credenciales inválidas";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenIssuer tokenIssuer) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        // Mismo encoder => mismo costo BCrypt que un hash real.
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    public String login(String username, String password) {
        User user = userRepository.findByUsername(username).orElse(null);

        String hashToCheck = user != null ? user.passwordHash() : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(password, hashToCheck);

        if (user == null || !passwordMatches) {
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }
        return tokenIssuer.issueToken(user);
    }
}
