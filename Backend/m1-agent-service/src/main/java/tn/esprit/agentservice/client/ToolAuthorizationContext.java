package tn.esprit.agentservice.client;

public final class ToolAuthorizationContext {
    private static final ThreadLocal<String> AUTHORIZATION = new ThreadLocal<>();

    private ToolAuthorizationContext() { }

    public static void set(String authorization) {
        AUTHORIZATION.set(authorization);
    }

    public static String get() {
        return AUTHORIZATION.get();
    }

    public static void clear() {
        AUTHORIZATION.remove();
    }
}
