package tn.esprit.agentservice.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public record CurrentUser(String id, boolean admin) {

    public static CurrentUser from(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            boolean admin = jwtAuthentication.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
            return new CurrentUser(jwtAuthentication.getToken().getSubject(), admin);
        }
        return new CurrentUser("dev-user", true);
    }
}
