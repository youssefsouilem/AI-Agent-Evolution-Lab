package tn.esprit.agentservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevSecurityWarning {
    private static final Logger logger = LoggerFactory.getLogger(DevSecurityWarning.class);

    @EventListener(ApplicationReadyEvent.class)
    public void warnSecurityIsDisabled() {
        logger.warn("""
                ****************************************************************
                * SECURITY DISABLED / dev identity simulation active           *
                * This profile is never evidence of production security.        *
                ****************************************************************
                """);
    }
}
