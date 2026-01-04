package com.example.online_shoe_store.Repository;

import com.example.online_shoe_store.Entity.ConversationMessage;
import com.example.online_shoe_store.Entity.enums.AgentType;
import com.example.online_shoe_store.Entity.enums.IntentType;
import com.example.online_shoe_store.Entity.enums.MessageRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, String> {
    
    /**
     * Tìm messages của conversation theo thứ tự thời gian
     */
    List<ConversationMessage> findByConversationIdOrderByCreatedAtAsc(String conversationId);
    
    /**
     * Tìm N messages gần nhất của conversation
     */
    @Query("SELECT m FROM ConversationMessage m WHERE m.conversation.id = :convId ORDER BY m.createdAt DESC")
    List<ConversationMessage> findRecentMessages(@Param("convId") String conversationId, Pageable pageable);
    
    /**
     * Tìm messages của conversation theo role
     */
    List<ConversationMessage> findByConversationIdAndRoleOrderByCreatedAtAsc(
        String conversationId, MessageRole role);
    
    /**
     * Tìm messages theo intent
     */
    List<ConversationMessage> findByPrimaryIntentOrderByCreatedAtDesc(IntentType intent);
    
    /**
     * Tìm messages theo agent
     */
    List<ConversationMessage> findByPrimaryAgentOrderByCreatedAtDesc(AgentType agent);
    
    /**
     * Tìm messages cần escalation
     */
    List<ConversationMessage> findByRequiredEscalationTrueOrderByCreatedAtDesc();
    
    /**
     * Đếm messages trong khoảng thời gian
     */
    @Query("SELECT COUNT(m) FROM ConversationMessage m WHERE m.createdAt BETWEEN :start AND :end")
    long countByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    
    /**
     * Tính avg processing time
     */
    @Query("SELECT AVG(m.processingTimeMs) FROM ConversationMessage m WHERE m.createdAt BETWEEN :start AND :end")
    Double avgProcessingTimeBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    
    /**
     * Thống kê theo intent
     */
    @Query("SELECT m.primaryIntent, COUNT(m) FROM ConversationMessage m " +
           "WHERE m.role = 'USER' GROUP BY m.primaryIntent")
    List<Object[]> countByIntent();


    List<ConversationMessage> findByConversationSessionIdOrderByCreatedAtAsc(String sessionId);

    /**
     * Tìm N messages gần nhất theo session ID (cho ContextManager)
     */
    @Query("SELECT m FROM ConversationMessage m WHERE m.conversation.sessionId = :sessionId ORDER BY m.createdAt DESC")
    List<ConversationMessage> findRecentBySessionId(@Param("sessionId") String sessionId, Pageable pageable);
    
    default List<ConversationMessage> findRecentBySessionId(String sessionId, int limit) {
        return findRecentBySessionId(sessionId, Pageable.ofSize(limit));
    }

    /**
     * Xóa messages theo session ID của conversation
     */
    void deleteByConversationSessionId(String sessionId);

    /**
     * Thống kê theo agent
     */
    @Query("SELECT m.primaryAgent, COUNT(m) FROM ConversationMessage m " +
           "WHERE m.primaryAgent IS NOT NULL GROUP BY m.primaryAgent")
    List<Object[]> countByAgent();
    
    /**
     * Tìm messages với confidence thấp (cần review)
     */
    @Query("SELECT m FROM ConversationMessage m WHERE m.confidence < :threshold ORDER BY m.createdAt DESC")
    List<ConversationMessage> findLowConfidenceMessages(@Param("threshold") Double threshold);
    
    /**
     * Paginated messages của conversation
     */
    Page<ConversationMessage> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable pageable);

    /**
     * Count messages by session ID (for hybrid memory summarization threshold)
     */
    @Query("SELECT COUNT(m) FROM ConversationMessage m WHERE m.conversation.sessionId = :sessionId")
    int countBySessionId(@Param("sessionId") String sessionId);
    
    /**
     * Find all messages by session ID ordered by created time (for summarization)
     */
    @Query("SELECT m FROM ConversationMessage m WHERE m.conversation.sessionId = :sessionId ORDER BY m.createdAt DESC")
    List<ConversationMessage> findAllBySessionIdOrderByCreatedAtDesc(@Param("sessionId") String sessionId);

}
