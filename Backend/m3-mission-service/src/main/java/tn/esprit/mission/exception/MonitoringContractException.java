package tn.esprit.mission.exception;

public class MonitoringContractException extends RuntimeException {
    public MonitoringContractException(String message) {
        super(message);
    }

    public MonitoringContractException(String message, Throwable cause) {
        super(message, cause);
    }
}
