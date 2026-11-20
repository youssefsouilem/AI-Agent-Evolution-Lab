package tn.esprit.agentservice.service;

import java.util.regex.Pattern;

public final class ErrorMessageSanitizer {
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(sk-[A-Za-z0-9_-]{8,}|Bearer\\s+\\S+|(?:api[\\s_-]?key\\s*[:=]\\s*)\\S+)"
    );

    private ErrorMessageSanitizer() { }

    public static String sanitize(String message) {
        if (message == null) {
            return null;
        }
        String safe = redact(message);
        return safe.length() > 1000 ? safe.substring(0, 1000) : safe;
    }

    public static String redact(String message) {
        return message == null ? null : SECRET.matcher(message).replaceAll("[REDACTED]");
    }
}
