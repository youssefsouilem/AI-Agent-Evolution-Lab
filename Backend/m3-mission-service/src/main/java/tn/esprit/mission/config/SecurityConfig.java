package tn.esprit.mission.config;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain security(HttpSecurity http, @Value("${security.enabled:false}") boolean enabled) throws Exception {
        http.cors(cors -> {}).csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        if (!enabled) return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Set<org.springframework.security.core.GrantedAuthority> authorities = new HashSet<>();
            Object realm = jwt.getClaims().get("realm_access");
            Object roles = realm instanceof Map<?, ?> values ? values.get("roles") : null;
            if (roles instanceof Collection<?> values) values.forEach(role -> {
                String name = role.toString().toUpperCase(Locale.ROOT);
                if (Set.of("ADMIN", "USER").contains(name)) authorities.add(new SimpleGrantedAuthority("ROLE_" + name));
            });
            return authorities;
        });
        return http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/error", "/actuator/health", "/actuator/health/**").permitAll()
            .requestMatchers(HttpMethod.PUT, "/api/orchestration-settings").hasRole("ADMIN")
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").hasRole("ADMIN")
            .anyRequest().hasAnyRole("USER", "ADMIN"))
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))).build();
    }

    @Bean
    @ConditionalOnProperty(name = "security.enabled", havingValue = "true")
    JwtDecoder jwtDecoder(@Value("${security.issuer-uri}") String issuer, @Value("${security.audience:m3-mission-service}") String audience) {
        NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuer);
        decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer), jwt -> jwt.getAudience().contains(audience)
                ? org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success()
                : org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(new org.springframework.security.oauth2.core.OAuth2Error("invalid_token", "Audience M3 absente", null))));
        return decoder;
    }
}
