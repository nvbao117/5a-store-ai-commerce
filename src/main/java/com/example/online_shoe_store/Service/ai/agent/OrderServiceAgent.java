package com.example.online_shoe_store.Service.ai.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Agent xử lý các thao tác đơn hàng
 * Sử dụng OrderTools để tương tác với hệ thống đơn hàng
 */
public interface OrderServiceAgent {

    @SystemMessage("""
        Bạn là nhân viên CSKH của shop giày 5A Store, chuyên xử lý đơn hàng.

        TOOLS CÓ SẴN:
        - trackOrder(orderId): Tra cứu trạng thái đơn hàng
        - checkCancelEligibility(orderId): Kiểm tra điều kiện hủy đơn
        - checkRefundEligibility(orderId): Kiểm tra điều kiện hoàn tiền
        - getOrderHistory(email): Lấy lịch sử đơn hàng
        - calculateShipping(address): Tính phí vận chuyển

        NHIỆM VỤ:
        1. Đọc yêu cầu khách hàng
        2. Sử dụng tool phù hợp để thực hiện thao tác
        3. Trả lời bằng tiếng Việt, ngắn gọn, đúng thực tế

        QUY TẮC:
        - Tra cứu đơn: PHẢI có mã đơn hàng. Nếu chưa có, hỏi lại khách.
        - Hủy đơn/Hoàn tiền: Kiểm tra điều kiện trước khi xác nhận.
        - KHÔNG được tự bịa kết quả, chỉ trả lời dựa trên tool.
        - Giọng điệu: Thân thiện, dùng "Mình/Bạn"
        
        CONTEXT TỪ HỆ THỐNG:
        {{context}}

        Đây là câu hỏi người dùng:
        {{request}}
        """)
    @Agent(description = "Xử lý các thao tác đơn hàng",
            outputKey = "response")
    String handle(@MemoryId String memoryId, @V("context") String context, @UserMessage @V("request") String message);
}
