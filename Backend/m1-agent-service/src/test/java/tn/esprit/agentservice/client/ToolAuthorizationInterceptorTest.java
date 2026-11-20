package tn.esprit.agentservice.client;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolAuthorizationInterceptorTest {

    @AfterEach
    void clearAuthorization() {
        ToolAuthorizationContext.clear();
    }

    @Test
    void forwardsCallerAuthorizationFromExecutionThreadContext() {
        ToolAuthorizationContext.set("Bearer test-user-token");
        RequestTemplate request = new RequestTemplate();

        new OpenFeignConfig().forwardCallerAuthorization().apply(request);

        assertEquals(List.of("Bearer test-user-token"), List.copyOf(request.headers().get("Authorization")));
    }

    @Test
    void doesNotAddAuthorizationWhenThereIsNoCallerHeader() {
        RequestTemplate request = new RequestTemplate();

        new OpenFeignConfig().forwardCallerAuthorization().apply(request);

        assertEquals(null, request.headers().get("Authorization"));
    }
}
