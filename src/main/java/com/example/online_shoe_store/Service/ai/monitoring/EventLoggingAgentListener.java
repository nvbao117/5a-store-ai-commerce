package com.example.online_shoe_store.Service.ai.monitoring;

import com.example.online_shoe_store.Entity.enums.ConversationEventType;
import com.example.online_shoe_store.Entity.enums.RiskLevel;
import com.example.online_shoe_store.Service.ai.execution.ConversationEventService;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.scope.AgenticScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AgentListener để ghi log và lưu events vào database + file TXT
 * Monitors tất cả agent invocations trong hệ thống
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EventLoggingAgentListener implements AgentListener {

    private final ConversationEventService eventService;
    private final AgentFileLogger fileLogger;
    private final Map<String, Long> agentStartTimes = new ConcurrentHashMap<>();

    @Override
    public boolean inheritedBySubagents() {
        return true; // Enable để track tất cả subagents
    }

    @Override
    public void afterAgenticScopeCreated(AgenticScope scope) {
        String sessionId = extractSessionId(scope);
        if (sessionId == null) return;

        // Extract userId from scope if available
        String userId = scope.hasState("userId") ? String.valueOf(scope.readState("userId")) : "unknown";

        // Log to file
        fileLogger.logSessionStart(sessionId, userId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("scopeId", scope.hashCode());
        payload.put("timestamp", System.currentTimeMillis());

        eventService.appendEventAsync(sessionId, ConversationEventType.SESSION_START, payload, null, null);
        log.debug("Agentic scope created for session: {}", sessionId);
    }

    @Override
    public void beforeAgentInvocation(AgentRequest req) {
        String sessionId = extractSessionId(req.agenticScope());
        if (sessionId == null) return;

        // Track start time
        String agentKey = sessionId + ":" + req.agentId();
        agentStartTimes.put(agentKey, System.currentTimeMillis());

        // Log to file - agent start with inputs
        fileLogger.logAgentStart(sessionId, req.agentId(), req.agentName(), req.inputs());

        Map<String, Object> payload = new HashMap<>();
        payload.put("agentId", req.agentId());
        payload.put("agentName", req.agentName());
        payload.put("inputs", sanitizeInputs(req.inputs()));
        payload.put("startTime", System.currentTimeMillis());

        eventService.appendEventAsync(sessionId, ConversationEventType.AGENT_RESPONSE, payload, null, null);
        log.debug("Agent {} starting for session: {}", req.agentId(), sessionId);
    }

    @Override
    public void afterAgentInvocation(AgentResponse res) {
        String sessionId = extractSessionId(res.agenticScope());
        if (sessionId == null) return;

        // Calculate duration
        String agentKey = sessionId + ":" + res.agentId();
        Long startTime = agentStartTimes.remove(agentKey);
        long durationMs = startTime != null ? System.currentTimeMillis() - startTime : 0;

        // Log to file - agent complete with output
        fileLogger.logAgentComplete(sessionId, res.agentId(), res.agentName(), res.output(), durationMs);

        Map<String, Object> payload = new HashMap<>();
        payload.put("agentId", res.agentId());
        payload.put("agentName", res.agentName());
        payload.put("inputs", sanitizeInputs(res.inputs()));
        payload.put("output", truncateOutput(res.output()));
        payload.put("durationMs", durationMs);
        payload.put("endTime", System.currentTimeMillis());

        eventService.appendEventAsync(sessionId, ConversationEventType.AGENT_RESPONSE, payload, null, null);
        log.info("Agent {} completed in {}ms for session: {}", res.agentId(), durationMs, sessionId);
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError err) {
        String sessionId = extractSessionId(err.agenticScope());
        if (sessionId == null) return;

        // Log to file - agent error with details
        fileLogger.logAgentError(sessionId, err.agentId(), err.agentName(), err.error(), err.inputs());

        Map<String, Object> payload = new HashMap<>();
        payload.put("agentId", err.agentId());
        payload.put("agentName", err.agentName());
        payload.put("inputs", sanitizeInputs(err.inputs()));
        payload.put("error", err.error().getMessage());
        payload.put("errorClass", err.error().getClass().getSimpleName());
        payload.put("stackTrace", truncateStackTrace(err.error()));
        payload.put("timestamp", System.currentTimeMillis());

        // Ghi event với risk level HIGH vì có lỗi
        eventService.appendEvent(sessionId, ConversationEventType.ERROR, payload, 
                null, null, RiskLevel.HIGH, null);
        
        log.error("Agent {} error for session: {} - {}. Scope keys: {}", 
                err.agentId(), sessionId, err.error().getMessage(), 
                err.agenticScope().state().keySet());
    }

    @Override
    public void beforeAgenticScopeDestroyed(AgenticScope scope) {
        String sessionId = extractSessionId(scope);
        if (sessionId == null) return;

        // Log to file - session end
        fileLogger.logSessionEnd(sessionId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("scopeId", scope.hashCode());
        payload.put("timestamp", System.currentTimeMillis());

        eventService.appendEventAsync(sessionId, ConversationEventType.SESSION_END, payload, null, null);
        log.debug("Agentic scope destroyed for session: {}", sessionId);
    }

    // ═══════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════

    /**
     * Extract session ID từ agentic scope
     */
    private String extractSessionId(AgenticScope scope) {
        if (scope == null) return null;
        
        // Try to get memoryId from scope (this is the primary identifier)
        Object memoryId = scope.memoryId();
        if (memoryId != null) {
            return String.valueOf(memoryId);
        }
        
        // Try to get from state
        if (scope.hasState("sessionId")) {
            Object sessionId = scope.readState("sessionId");
            if (sessionId != null) {
                return String.valueOf(sessionId);
            }
        }
        
        // Fallback to scope hashcode as identifier
        return "scope-" + scope.hashCode();
    }

    /**
     * Sanitize inputs để tránh log sensitive data
     */
    private Map<String, Object> sanitizeInputs(Map<String, Object> inputs) {
        if (inputs == null) return Map.of();
        
        Map<String, Object> sanitized = new HashMap<>();
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // Mask sensitive fields
            if (key.toLowerCase().contains("password") || 
                key.toLowerCase().contains("token") ||
                key.toLowerCase().contains("secret")) {
                sanitized.put(key, "***MASKED***");
            } else if (value instanceof String str && str.length() > 500) {
                sanitized.put(key, str.substring(0, 500) + "...[truncated]");
            } else {
                sanitized.put(key, value);
            }
        }
        return sanitized;
    }

    /**
     * Truncate output để tránh log quá dài
     */
    private String truncateOutput(Object output) {
        if (output == null) return null;
        String str = String.valueOf(output);
        if (str.length() > 1000) {
            return str.substring(0, 1000) + "...[truncated]";
        }
        return str;
    }

    /**
     * Get truncated stack trace
     */
    private String truncateStackTrace(Throwable error) {
        if (error == null) return null;
        
        StringBuilder sb = new StringBuilder();
        StackTraceElement[] elements = error.getStackTrace();
        int maxElements = Math.min(5, elements.length);
        
        for (int i = 0; i < maxElements; i++) {
            sb.append(elements[i].toString()).append("\n");
        }
        
        if (elements.length > maxElements) {
            sb.append("... ").append(elements.length - maxElements).append(" more");
        }
        
        return sb.toString();
    }
}
