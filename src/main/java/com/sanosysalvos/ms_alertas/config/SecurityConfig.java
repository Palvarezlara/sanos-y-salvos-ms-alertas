package com.sanosysalvos.ms_alertas.config;

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
                // Lectura de alertas de zona/mapa es de acceso libre para invitados,
                // igual que Home/Listado en ms-mascotas (ver CLAUDE.md). Crear,
                // actualizar o eliminar sigue requiriendo un access_token válido.
                .requestMatchers(HttpMethod.GET, "/alertas", "/alertas/zona").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.decoder(jwtDecoder()))
            );
        return http.build();
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
