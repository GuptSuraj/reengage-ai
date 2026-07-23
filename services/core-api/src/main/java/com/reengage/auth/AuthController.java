package com.reengage.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    AuthController(JdbcClient jdbc, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    @Transactional
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        var id = UUID.randomUUID();
        var role = "CUSTOMER";
        jdbc.sql("""
                INSERT INTO app_user(id,email,password_hash,display_name,role)
                VALUES (:id,:email,:password,:name,:role)
                """)
                .param("id", id)
                .param("email", request.email().toLowerCase())
                .param("password", passwordEncoder.encode(request.password()))
                .param("name", request.displayName())
                .param("role", role)
                .update();
        jdbc.sql("INSERT INTO user_preference(user_id) VALUES (:id)").param("id", id).update();
        return new AuthResponse(jwtService.issue(id, request.email(), role), id, request.email(), role);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        var user = jdbc.sql("""
                SELECT id,email,password_hash,role FROM app_user WHERE email=:email AND active=true
                """).param("email", request.email().toLowerCase())
                .query(UserRow.class).optional()
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }
        return new AuthResponse(jwtService.issue(user.id(), user.email(), user.role()),
                user.id(), user.email(), user.role());
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        var userId = (UUID) authentication.getPrincipal();
        return jdbc.sql("SELECT id,email,display_name,role,created_at FROM app_user WHERE id=:id")
                .param("id", userId).query().singleRow();
    }

    public record RegisterRequest(@Email String email, @Size(min = 8, max = 72) String password,
                                  @NotBlank @Size(max = 80) String displayName) {}
    public record LoginRequest(@Email String email, @NotBlank String password) {}
    public record AuthResponse(String accessToken, UUID userId, String email, String role) {}
    private record UserRow(UUID id, String email, String passwordHash, String role) {}
}
