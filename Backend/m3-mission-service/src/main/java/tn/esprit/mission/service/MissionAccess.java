package tn.esprit.mission.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tn.esprit.mission.entity.Mission;

public final class MissionAccess {
    private MissionAccess() {}
    public static String subject() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken jwt ? jwt.getName() : null;
    }
    public static boolean admin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_ADMIN"));
    }
    public static void check(Mission mission) {
        String subject = subject();
        if (subject != null && !admin() && !subject.equals(mission.getOwnerId())) {
            throw new AccessDeniedException("Vous n'avez pas accès à cette mission.");
        }
    }
}
