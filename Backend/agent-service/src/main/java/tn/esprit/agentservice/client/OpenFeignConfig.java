package tn.esprit.agentservice.client;

import feign.Request;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tn.esprit.agentservice.exception.DownstreamServiceException;
import tn.esprit.agentservice.exception.DownstreamTimeoutException;

import java.util.concurrent.TimeUnit;

@Configuration
public class OpenFeignConfig {

    @Bean
    public Request.Options toolRequestOptions() {
        return new Request.Options(1, TimeUnit.SECONDS, 3, TimeUnit.SECONDS, true);
    }

    @Bean
    public ErrorDecoder toolErrorDecoder() {
        return (methodKey, response) -> {
            String message = "Tool service request failed with HTTP " + response.status() + ".";
            if (response.status() == 408 || response.status() == 504) {
                return new DownstreamTimeoutException(message);
            }
            return new DownstreamServiceException(message);
        };
    }

    @Bean
    public feign.Retryer toolRetryer() {
        return feign.Retryer.NEVER_RETRY;
    }

    public static RuntimeException mapTransportFailure(RetryableException exception) {
        if (hasTimeoutCause(exception)) {
            return new DownstreamTimeoutException("Tool service request timed out.", exception);
        }
        return new DownstreamServiceException("Tool service is unavailable.", exception);
    }

    private static boolean hasTimeoutCause(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.SocketTimeoutException
                    || cause.getClass().getSimpleName().contains("Timeout")) {
                return true;
            }
        }
        return false;
    }
}
