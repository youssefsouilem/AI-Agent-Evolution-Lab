package tn.esprit.agentservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecutionRegressionTest {

    @Test
    void maxTurnsOneAllowsFirstToolCallAndRejectsSecond() {
        AtomicInteger calls = new AtomicInteger();
        ToolCallback callback = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("example").description("test")
                        .inputSchema("{\"type\":\"object\"}").build();
            }

            @Override
            public String call(String input) {
                calls.incrementAndGet();
                return "ok";
            }
        };

        AgentExecutionService service = new AgentExecutionService(
                null, null, null, null, null, null, null, null, null, null, null, null
        );
        ToolCallRecorder recorder = new ToolCallRecorder();
        ToolCallback limited = limit(service, callback, 1, new AtomicInteger(), recorder);
        assertEquals("ok", limited.call("{}"));
        assertThrows(RuntimeException.class, () -> limited.call("{}"));
        assertEquals(1, calls.get());
    }

    private ToolCallback limit(
            AgentExecutionService service,
            ToolCallback callback,
            int maxTurns,
            AtomicInteger turns,
            ToolCallRecorder recorder
    ) {
        try {
            Method method = AgentExecutionService.class.getDeclaredMethod(
                    "limitTurns", ToolCallback.class, int.class, AtomicInteger.class, ToolCallRecorder.class
            );
            method.setAccessible(true);
            return (ToolCallback) method.invoke(service, callback, maxTurns, turns, recorder);
        } catch (ReflectiveOperationException exception) {
            if (exception instanceof InvocationTargetException invocation && invocation.getCause() != null) {
                throw new IllegalStateException(invocation.getCause());
            }
            throw new IllegalStateException(exception);
        }
    }
}
