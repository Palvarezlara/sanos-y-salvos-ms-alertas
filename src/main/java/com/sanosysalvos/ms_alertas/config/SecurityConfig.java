package com.sanosysalvos.ms_alertas.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Segunda capa de validación de JWT (la primera es el API Gateway). Los access
 * tokens de Cognito no traen `aud`, así que se valida `client_id` + `token_use`
 * en su lugar (ver nota en CLAUDE.md, sección "Autenticación").
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${cognito.client-id}")
    private String clientId;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                // Lectura de alertas de zona/mapa es de acceso libre para invitados,
                // igual que Home/Listado en ms-mascotas (ver CLAUDE.md). Crear,
                // actualizar o eliminar sigue requiriendo un access_token válido.
                .requestMatchers(HttpMethod.GET, "/alertas", "/alertas/zona").permitAll()
                // Las alertas las crea y desactiva el flujo de mensajería (listeners SQS), no
                // los usuarios: escribir a mano queda solo para administradores (grupo
                // "Administradores" de Cognito), no para cualquier usuario con sesión.
                .requestMatchers(HttpMethod.POST, "/alertas").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.PATCH, "/alertas/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.DELETE, "/alertas/**").hasRole("ADMINISTRADOR")
                // cualquiera que encuentre una mascota puede contactar, sin cuenta
                .requestMatchers(HttpMethod.POST, "/contactos").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.decoder(jwtDecoder()).jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );
        return http.build();
    }

    // Mismo criterio que ms-mascotas: el grupo "Administradores" de Cognito viaja en el
    // claim cognito:groups del access token y se traduce al rol ADMINISTRADOR.
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
            List<String> grupos = jwt.getClaimAsStringList("cognito:groups");
            if (grupos != null && grupos.contains("Administradores")) {
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));
            }
            return authorities;
        });
        return converter;
    }

    private NimbusJwtDecoder jwtDecoder() {
        // withJwkSetUri no hace ninguna llamada de red al crear el bean — las
        // claves se piden recién al validar el primer token. fromIssuerLocation
        // sí hace una llamada eager al arrancar, lo que rompe los tests y hace
        // frágil el arranque si Cognito no responde justo en ese momento.
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withClientId = clientIdValidator();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withClientId));

        return decoder;
    }

    private OAuth2TokenValidator<Jwt> clientIdValidator() {
        return token -> {
            boolean correctUse = "access".equals(token.getClaimAsString("token_use"));
            boolean correctClient = clientId.equals(token.getClaimAsString("client_id"));
            if (correctUse && correctClient) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token", "El token no fue emitido para esta aplicación", null));
        };
    }
}
