package com.example.online_shoe_store.Controller.api;

import com.example.online_shoe_store.dto.request.ApiChatRequest;
import com.example.online_shoe_store.dto.response.ChatResponse;
import com.example.online_shoe_store.Service.ChatBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatBotAPIController {

    private final ChatBotService chatBotService;
    
    /**
     * Traditional synchronous endpoint
     */
    @PostMapping("/send")
    public ResponseEntity<ChatResponse> sendMessage(@RequestBody ApiChatRequest request) {
        log.info("Received chat request: message='{}', sessionId='{}'",
                request.getMessage(), request.getSessionId());
        ChatResponse response = chatBotService.processMessage(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Streaming endpoint
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(
           @RequestParam String message,
            @RequestParam(required = false) String sessionId) {
        
        log.info("Received streaming chat request: message='{}', sessionId='{}'", message, sessionId);
        
        SseEmitter emitter = new SseEmitter(60000L); // 60 second timeout
        
        // Use pseudo-streaming (token-by-token simulation)
        // Pass null for streamingModel as we are using the fake streaming implementation
        chatBotService.processMessageStreaming(message, sessionId, emitter, null);
        
        return emitter;
    }
}
