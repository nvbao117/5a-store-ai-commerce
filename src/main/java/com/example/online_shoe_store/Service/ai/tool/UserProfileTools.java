package com.example.online_shoe_store.Service.ai.tool;

import com.example.online_shoe_store.Entity.UserProfileMemory;
import com.example.online_shoe_store.Repository.UserProfileMemoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * User Profile Tools - Fortified & Safe
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UserProfileTools {

    private final UserProfileMemoryRepository profileRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Whitelist các key được phép lưu vào profile preference
    private static final Set<String> ALLOWED_PREFERENCE_KEYS = Set.of(
            "preferred_size", 
            "preferred_brand", 
            "default_shipping",
            "price_range",
            "style"
    );

    @Tool("Lấy thông tin profile và preferences của user. Trả về thông tin như size ưa thích, thương hiệu yêu thích, sở thích...")
    public String getUserProfile(String userId) {
        log.info("[UserProfileTools] Getting profile for user: {}", userId);
        
        if (userId == null || userId.isBlank() || "guest".equals(userId)) {
            return "Khách chưa đăng nhập nên không có thông tin profile lưu trữ.";
        }
        
        try {
            return profileRepository.findByUserId(userId)
                    .map(profile -> {
                        StringBuilder sb = new StringBuilder();
                        sb.append("Thông tin User Profile:\n");
                        sb.append("- Số cuộc hội thoại: ").append(profile.getConversationCount()).append("\n");
                        sb.append("- Số đơn hàng: ").append(profile.getOrderCount()).append("\n");
                        
                        if (profile.getProfileJson() != null && !profile.getProfileJson().isBlank()) {
                            sb.append("- Preferences: ").append(profile.getProfileJson()).append("\n");
                        }
                        
                        if (profile.getInteractionSummary() != null && !profile.getInteractionSummary().isBlank()) {
                            sb.append("- Tóm tắt tương tác: ").append(profile.getInteractionSummary()).append("\n");
                        }
                        
                        // Last active
                        if (profile.getLastActiveAt() != null) {
                             sb.append("- Last Active: ").append(profile.getLastActiveAt()).append("\n");
                        }
                        
                        return sb.toString();
                    })
                    .orElse("Chưa có thông tin profile cho user này.");
        } catch (Exception e) {
            log.error("[UserProfileTools] Error getting profile: {}", e.getMessage());
            return "Không thể lấy thông tin profile lúc này.";
        }
    }

    @Tool("Cập nhật size giày ưa thích của user. Ví dụ: updatePreferredSize('user123', '42')")
    public String updatePreferredSize(String userId, String size) {
        return updatePreference(userId, "preferred_size", size, 
                "Đã lưu size giày ưa thích của bạn là " + size);
    }

    @Tool("Cập nhật thương hiệu yêu thích của user. Ví dụ: updatePreferredBrand('user123', 'Nike')")
    public String updatePreferredBrand(String userId, String brand) {
        return updatePreference(userId, "preferred_brand", brand, 
                "Đã lưu thương hiệu yêu thích của bạn là " + brand);
    }

    @Tool("Thêm sở thích vào profile user. Ví dụ: addInterest('user123', 'chạy bộ')")
    public String addInterest(String userId, String interest) {
        log.info("[UserProfileTools] Adding interest for user: {} - {}", userId, interest);
        
        if (userId == null || userId.isBlank() || "guest".equals(userId)) {
            return "Không thể lưu sở thích cho khách chưa đăng nhập.";
        }
        
        // Simple retry logic cho optimistic locking
        int maxRetries = 2;
        for (int i = 0; i <= maxRetries; i++) {
            try {
                UserProfileMemory profile = getOrCreateProfile(userId);
                ObjectNode profileJson = parseProfileJson(profile.getProfileJson());
                
                // Thêm interest vào array
                if (!profileJson.has("interests")) {
                    profileJson.putArray("interests");
                }
                
                var interestsArray = profileJson.withArray("interests");
                boolean exists = false;
                for (JsonNode node : interestsArray) {
                    if (node.asText().equalsIgnoreCase(interest)) {
                        exists = true;
                        break;
                    }
                }
                
                if (!exists) {
                    interestsArray.add(interest);
                }
                
                profile.setProfileJson(objectMapper.writeValueAsString(profileJson));
                profile.setLastActiveAt(LocalDateTime.now());
                profileRepository.save(profile);
                
                return "Đã thêm sở thích '" + interest + "' vào profile của bạn.";
                
            } catch (ObjectOptimisticLockingFailureException ople) {
                log.warn("[UserProfileTools] Optimistic locking failure (attempt {}): {}", i, ople.getMessage());
                if (i == maxRetries) return "Không thể cập nhật do đang có xử lý khác. Vui lòng thử lại.";
                // Retry
            } catch (Exception e) {
                log.error("[UserProfileTools] Error adding interest: {}", e.getMessage());
                return "Không thể cập nhật sở thích lúc này.";
            }
        }
        return "Lỗi không xác định.";
    }

    @Tool("Cập nhật phương thức giao hàng ưa thích. Ví dụ: updateShippingPreference('user123', 'express')")
    public String updateShippingPreference(String userId, String shippingType) {
        return updatePreference(userId, "default_shipping", shippingType, 
                "Đã lưu phương thức giao hàng ưa thích của bạn là " + shippingType);
    }

    @Tool("Lưu ghi chú về user để nhớ cho các cuộc hội thoại sau. Ví dụ: saveUserNote('user123', 'Khách hay hỏi về giày chạy bộ')")
    public String saveUserNote(String userId, String note) {
        log.info("[UserProfileTools] Saving note for user: {}", userId);
        
        if (userId == null || userId.isBlank() || "guest".equals(userId)) {
            return "Không thể lưu ghi chú cho khách chưa đăng nhập.";
        }
        
        int maxRetries = 2;
        for (int i = 0; i <= maxRetries; i++) {
            try {
                UserProfileMemory profile = getOrCreateProfile(userId);
                
                String currentSummary = profile.getInteractionSummary();
                String newSummary = currentSummary != null && !currentSummary.isBlank()
                        ? currentSummary + "\n" + note
                        : note;
                
                profile.setInteractionSummary(newSummary);
                profile.setLastActiveAt(LocalDateTime.now());
                profileRepository.save(profile);
                
                return "Đã lưu ghi chú về khách hàng.";
            } catch (ObjectOptimisticLockingFailureException ople) {
                log.warn("[UserProfileTools] Optimistic locking failure (attempt {}): {}", i, ople.getMessage());
                if (i == maxRetries) return "Hệ thống bận, chưa lưu được ghi chú.";
            } catch (Exception e) {
                log.error("[UserProfileTools] Error saving note: {}", e.getMessage());
                return "Không thể lưu ghi chú lúc này.";
            }
        }
        return "Lỗi không xác định.";
    }

    // ============ HELPER METHODS ============

    private String updatePreference(String userId, String key, String value, String successMessage) {
        if (userId == null || userId.isBlank() || "guest".equals(userId)) {
            return "Không thể lưu preference cho khách chưa đăng nhập.";
        }
        
        // Whitelist check
        if (!ALLOWED_PREFERENCE_KEYS.contains(key)) {
            log.warn("[UserProfileTools] Blocked attempt to save unauthorized key: {}", key);
            return "Không được phép lưu thông tin '" + key + "'.";
        }
        
        log.info("[UserProfileTools] Updating preference '{}' for user: {}", key, userId);
        
        int maxRetries = 2;
        for (int i = 0; i <= maxRetries; i++) {
            try {
                UserProfileMemory profile = getOrCreateProfile(userId);
                ObjectNode profileJson = parseProfileJson(profile.getProfileJson());
                
                profileJson.put(key, value);
                
                profile.setProfileJson(objectMapper.writeValueAsString(profileJson));
                profile.setLastActiveAt(LocalDateTime.now());
                profileRepository.save(profile);
                
                return successMessage;
                
            } catch (ObjectOptimisticLockingFailureException ople) {
                log.warn("[UserProfileTools] Optimistic locking failure (attempt {}): {}", i, ople.getMessage());
                if (i == maxRetries) return "Hệ thống bận, vui lòng thử lại sau.";
            } catch (Exception e) {
                log.error("[UserProfileTools] Error updating preference: {}", e.getMessage());
                return "Không thể cập nhật thông tin lúc này.";
            }
        }
        return "Lỗi không xác định.";
    }

    private UserProfileMemory getOrCreateProfile(String userId) {
        // Luôn fetch mới để có version mới nhất
        return profileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserProfileMemory newProfile = UserProfileMemory.builder()
                            .userId(userId)
                            .profileJson("{}")
                            .conversationCount(0)
                            .orderCount(0)
                            .build();
                    return profileRepository.save(newProfile);
                });
    }

    private ObjectNode parseProfileJson(String json) {
        try {
            if (json == null || json.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return (ObjectNode) objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }
}
