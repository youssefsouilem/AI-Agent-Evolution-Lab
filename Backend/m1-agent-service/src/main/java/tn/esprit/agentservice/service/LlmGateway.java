package tn.esprit.agentservice.service;

public interface LlmGateway {
    LlmResult call(LlmRequest request);
}
