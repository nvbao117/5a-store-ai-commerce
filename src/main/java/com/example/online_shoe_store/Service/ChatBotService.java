package com.example.online_shoe_store.Service;

import com.example.online_shoe_store.Entity.Conversation;
import com.example.online_shoe_store.Entity.ConversationMessage;
import com.example.online_shoe_store.Entity.User;
import com.example.online_shoe_store.Entity.enums.MessageRole;
import com.example.online_shoe_store.Repository.ConversationMessageRepository;
import com.example.online_shoe_store.Repository.ConversationRepository;
import com.example.online_shoe_store.Repository.UserRepository;
import com.example.online_shoe_store.Service.ai.agent.ShopAssistantAgent;
import com.example.online_shoe_store.Service.ai.context.DirectContextService;
import com.example.online_shoe_store.Service.ai.context.ProductJsonHolder;
import com.example.online_shoe_store.Service.ai.monitoring.AgentFileLogger;
import com.example.online_shoe_store.Service.ai.monitoring.ToolLoggingChatModelListener;
import com.example.online_shoe_store.dto.request.ApiChatRequest;
import com.example.online_shoe_store.dto.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatBotService {

    private final ShopAssistantAgent shopAssistantAgent;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final AgentFileLogger agentFileLogger;
    private final com.example.online_shoe_store.Service.ai.context.DirectContextService directContextService;
    private final ProductJsonHolder productJsonHolder;

    @Transactional
    public ChatResponse processMessage(ApiChatRequest request) {
        long startTime = System.currentTimeMillis();

        String userId = getAuthenticatedUserId();
        User user = "guest".equals(userId) ? null : userRepository.findById(userId).orElse(null);

        String sessionId = (request.getSessionId() != null && !request.getSessionId().isBlank())
                ? request.getSessionId()
                : UUID.randomUUID().toString();

        String userMessage = request.getMessage();

        log.debug("Chat: userId={}, sessionId={}, message={}", userId, sessionId, userMessage);

        // Log vào file
        agentFileLogger.logUserMessage(sessionId, userMessage);

        ToolLoggingChatModelListener.setCurrentSession(sessionId);

        // 1.Lấy hoặc tạo hội thoại mới
        Conversation conversation = conversationRepository.findBySessionIdAndIsActiveTrue(sessionId)
                .orElseGet(() -> {
                    Conversation newConv = Conversation.builder()
                            .sessionId(sessionId)
                            .user(user)
                            .build();
                    return conversationRepository.save(newConv);
                });

        // 2. Lưu message
        ConversationMessage userMsg = ConversationMessage.builder()
                .conversation(conversation)
                .role(MessageRole.USER)
                .content(userMessage)
                .build();
        messageRepository.save(userMsg);
        conversation.setMessageCount(conversation.getMessageCount() + 1);

        // 3. DIRECT context fetch
        long contextStart = System.currentTimeMillis();
        String context = directContextService.prepareContext(sessionId, userId, userMessage);
        log.info("Context prepared in {}ms", System.currentTimeMillis() - contextStart);

        String answer;
        try {
            answer = shopAssistantAgent.chat(sessionId, userId, userMessage, context);
            answer = productJsonHolder.ensureJsonBlock(sessionId, answer);
        } catch (Exception e) {
            log.error("Agent error: {}", e.getMessage(), e);
            answer = "Xin lỗi, có lỗi xảy ra. Vui lòng thử lại sau.";
        } finally {
            ToolLoggingChatModelListener.clearCurrentSession();
            productJsonHolder.clearSession(sessionId);
        }

        long duration = System.currentTimeMillis() - startTime;
        ConversationMessage botMsg = ConversationMessage.builder()
                .conversation(conversation)
                .role(MessageRole.ASSISTANT)
                .content(answer)
                .processingTimeMs((int) duration)
                .build();
        messageRepository.save(botMsg);
        conversation.setMessageCount(conversation.getMessageCount() + 1);

        agentFileLogger.logAssistantResponse(sessionId, answer, duration);

        log.info("Chat completed: {}ms, session={}", duration, sessionId);

        return ChatResponse.builder()
                .sessionId(sessionId)
                .response(answer)
                .type("TEXT")
                .build();
    }

    private String getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return "guest";
        }

        String username = auth.getName();

        return userRepository.findByUsername(username)
                .map(user -> user.getUserId())
                .orElse("guest");
    }
}

