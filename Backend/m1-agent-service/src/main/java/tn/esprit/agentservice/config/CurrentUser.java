package tn.esprit.agentservice.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public record CurrentUser(String id, boolean admin, String displayName) {
    public CurrentUser(String id, boolean admin) {
        this(id, admin, id);
    }

    public static CurrentUser from(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            boolean admin = jwtAuthentication.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
            String id = jwtAuthentication.getToken().getSubject();
            Object preferredUsername = jwtAuthentication.getToken().getClaims().get("preferred_username");
            Object name = jwtAuthentication.getToken().getClaims().get("name");
            String displayName = preferredUsername instanceof String value && !value.isBlank()
                    ? value : name instanceof String value && !value.isBlank() ? value : id;
            return new CurrentUser(id, admin, displayName);
        }
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return new CurrentUser("dev-user", false);
        }
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return new CurrentUser(authentication.getName(), admin);
    }

    public String role() {
        return admin ? "ADMIN" : "USER";
    }
}
