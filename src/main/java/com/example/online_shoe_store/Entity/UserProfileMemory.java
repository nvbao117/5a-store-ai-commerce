package com.example.online_shoe_store.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Long-term user memory (cross-session preferences)
 * Lưu thông tin như: preferred_size, preferred_brand, shipping preferences...
 */
@Entity
@Table(name = "user_profile_memory", indexes = {
    @Index(name = "idx_user_profile_user_id", columnList = "userId")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileMemory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Optimistic locking để tránh race condition khi nhiều async job cập nhật cùng lúc
     */
    @Version
    private Long version;
    
    @Column(nullable = false, unique = true)
    private String userId;
    
    /**
     * User preferences as JSON
     * {
     *   "preferred_size": "42",
     *   "preferred_brand": "Nike",
     *   "default_shipping": "express",
     *   "interests": ["running", "basketball"]
     * }
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String profileJson;
    
    /**
     * Summary of past interactions
     */
    @Column(columnDefinition = "TEXT")
    private String interactionSummary;
    
    /**
     * Total number of conversations
     */
    @Builder.Default
    private Integer conversationCount = 0;
    
    /**
     * Total number of orders
     */
    @Builder.Default
    private Integer orderCount = 0;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    /**
     * Last active time
     */
    private LocalDateTime lastActiveAt;
}
