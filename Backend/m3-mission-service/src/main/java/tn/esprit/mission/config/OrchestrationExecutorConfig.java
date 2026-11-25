package tn.esprit.mission.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class OrchestrationExecutorConfig {
    @Bean("orchestrationExecutor")
    public ThreadPoolTaskExecutor orchestrationExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("orchestrator-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
