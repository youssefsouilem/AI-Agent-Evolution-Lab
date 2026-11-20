package tn.esprit.agentservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableScheduling
public class RabbitMQConfig {

    @Bean
    public TopicExchange agentEventsExchange(
            @Value("${app.events.agent-exchange:agent.events}") String exchange
    ) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService agentExecutionExecutor() {
        return Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "agent-execution");
            thread.setDaemon(true);
            return thread;
        });
    }
}
