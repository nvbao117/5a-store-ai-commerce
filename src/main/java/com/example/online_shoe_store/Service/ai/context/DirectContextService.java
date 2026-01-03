package com.example.online_shoe_store.Service.ai.context;

import com.example.online_shoe_store.Repository.ConversationMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class DirectContextService {

    private final ProductJsonHolder productJsonHolder;

    public String prepareContext(String sessionId, String userId, String userMessage) {
        StringBuilder context = new StringBuilder();

        String productJson = productJsonHolder.getJsonForSession(sessionId);
        if (productJson != null && !productJson.isEmpty()) {
            context.append("Product Information (JSON):\n")
                   .append(productJson)
                   .append("\n\n");
        }
        
        context.append("Current Session ID: ").append(sessionId).append("\n");
        context.append("User ID: ").append(userId).append("\n");

        return context.toString();
    }
}
