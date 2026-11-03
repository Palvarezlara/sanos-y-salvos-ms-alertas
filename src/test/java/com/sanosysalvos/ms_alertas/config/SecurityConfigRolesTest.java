package com.sanosysalvos.ms_alertas.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/* El grupo "Administradores" de Cognito debe traducirse al rol ADMINISTRADOR, igual que en ms-mascotas */
class SecurityConfigRolesTest {

    private final SecurityConfig config = new SecurityConfig();

    private Jwt jwt(Object grupos) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none").subject("usuario-1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (grupos != null) {
            builder.claim("cognito:groups", grupos);
        }
        return builder.build();
    }

    private List<String> authorities(Jwt jwt) {
        return config.jwtAuthenticationConverter().convert(jwt).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void elGrupoAdministradoresOtorgaElRolAdministrador() {
        assertThat(authorities(jwt(List.of("Administradores")))).contains("ROLE_ADMINISTRADOR");
    }

    @Test
    void sinGrupoOConOtroGrupoNoOtorgaElRol() {
        assertThat(authorities(jwt(null))).doesNotContain("ROLE_ADMINISTRADOR");
        assertThat(authorities(jwt(List.of("Moderadores")))).doesNotContain("ROLE_ADMINISTRADOR");
    }
}
