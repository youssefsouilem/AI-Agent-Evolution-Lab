package tn.esprit.agentservice.service;

import java.util.ArrayList;
import java.util.List;

public class ToolCallRecorder {
    private final List<ToolCallRecord> calls = java.util.Collections.synchronizedList(new ArrayList<>());

    public void add(ToolCallRecord call) {
        calls.add(call);
    }

    public List<ToolCallRecord> snapshot() {
        synchronized (calls) {
            return List.copyOf(calls);
        }
    }

    public int count() {
        return calls.size();
    }
}
