package tn.esprit.agentservice.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class SpringAiLlmGateway implements LlmGateway {
    private final ChatClient.Builder chatClientBuilder;

    public SpringAiLlmGateway(ChatClient.Builder chatClientBuilder) {
        this.chatClientBuilder = chatClientBuilder;
    }

    @Override
    public LlmResult call(LlmRequest request) {
        var options = OpenAiChatOptions.builder()
                .model(request.model())
                .temperature(request.settings().temperature())
                .maxTokens(request.settings().maxTokens())
                .timeout(Duration.ofSeconds(request.settings().timeoutSeconds()));
        ChatClient.ChatClientRequestSpec prompt = chatClientBuilder.build()
                .prompt()
                .system(request.systemPrompt())
                .user(request.userMessage())
                .options(options);
        if (!request.toolCallbacks().isEmpty()) {
            prompt.toolCallbacks(request.toolCallbacks());
        }
        ChatResponse response = prompt.call().chatResponse();
        String text = response == null || response.getResult() == null
                || response.getResult().getOutput() == null
                ? null : response.getResult().getOutput().getText();
        Integer promptTokens = null;
        Integer completionTokens = null;
        Integer totalTokens = null;
        if (response != null && response.getMetadata() != null && response.getMetadata().getUsage() != null) {
            var usage = response.getMetadata().getUsage();
            promptTokens = usage.getPromptTokens();
            completionTokens = usage.getCompletionTokens();
            totalTokens = usage.getTotalTokens();
        }
        return new LlmResult(text, promptTokens, completionTokens, totalTokens);
    }
}
