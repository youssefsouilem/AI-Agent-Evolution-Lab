package tn.esprit.agentservice.exception;

public class AgentQuotaExceededException extends RuntimeException {
    private final int limit;
    private final long usage;

    public AgentQuotaExceededException(int limit, long usage) {
        super("Limite de " + limit + " agents atteinte. Supprimez un agent ou contactez un administrateur.");
        this.limit = limit;
        this.usage = usage;
    }

    public int getLimit() {
        return limit;
    }

    public long getUsage() {
        return usage;
    }
}
