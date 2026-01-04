package com.example.online_shoe_store.Service.ai.context;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Captures PRODUCTS_JSON blocks from tool results and ensures they appear in final response.
 * This is a workaround for LLM sometimes ignoring instructions to copy the JSON block.
 */
@Slf4j
@Component
public class ProductJsonHolder {
    
    private static final Pattern JSON_BLOCK_PATTERN = 
        Pattern.compile("\\[PRODUCTS_JSON\\]([\\s\\S]*?)\\[/PRODUCTS_JSON\\]");
    
    private final ConcurrentHashMap<String, String> jsonBlocks = new ConcurrentHashMap<>();
    

    public void captureFromToolResult(String sessionId, String toolResult) {
        if (toolResult == null || sessionId == null) return;
        
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(toolResult);
        if (matcher.find()) {
            String jsonBlock = matcher.group(0);
            jsonBlocks.put(sessionId, jsonBlock);
            log.debug("[ProductJsonHolder] Captured JSON block for session {}: {} chars", 
                     sessionId, jsonBlock.length());
        }
    }

    public String ensureJsonBlock(String sessionId, String response) {
        if (response == null || sessionId == null) return response;
        
        // Check if response already has JSON block
        if (JSON_BLOCK_PATTERN.matcher(response).find()) {
            // Clear stored block as it's already in response
            jsonBlocks.remove(sessionId);
            return response;
        }
        
        String storedBlock = jsonBlocks.remove(sessionId);
        if (storedBlock == null) {
            return response;
        }
        
        log.info("[ProductJsonHolder] Injecting JSON block into response for session {}", sessionId);
        
        // Find good injection point - after first line or paragraph
        int injectionPoint = response.indexOf("\n\n");
        if (injectionPoint == -1) {
            injectionPoint = response.indexOf("\n");
        }
        if (injectionPoint == -1) {
            injectionPoint = response.length();
        }
        
        StringBuilder result = new StringBuilder();
        result.append(response, 0, injectionPoint);
        result.append("\n\n");
        result.append(storedBlock);
        if (injectionPoint < response.length()) {
            result.append(response.substring(injectionPoint));
        }
        
        return result.toString();
    }
    
    /**
     * Clear session data
     */
    public void clearSession(String sessionId) {
        if (sessionId != null) {
            jsonBlocks.remove(sessionId);
        }
    }

    public String getJsonForSession(String sessionId) {
        return sessionId != null ? jsonBlocks.get(sessionId) : null;
    }
}
