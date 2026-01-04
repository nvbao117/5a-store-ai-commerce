package com.example.online_shoe_store.Service.ai.tool;

import com.example.online_shoe_store.Entity.Order;
import com.example.online_shoe_store.Repository.OrderRepository;
import com.example.online_shoe_store.Repository.UserRepository;
import com.example.online_shoe_store.Service.ai.quality.HumanApprovalService;
import com.example.online_shoe_store.Entity.enums.OrderStatus;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Order Tools - Công cụ cho Order/Logistics Agent
 * Tích hợp Human-in-the-Loop cho các action nhạy cảm
 * Cải thiện Error Recovery với gợi ý cụ thể
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderTools {

    private final OrderRepository orderRepository;
    private final HumanApprovalService humanApprovalService;
    private final UserRepository userRepository;

    private static final NumberFormat VND_FORMAT = NumberFormat.getInstance(new Locale("vi", "VN"));

    @Tool("Tra cứu đơn hàng theo mã đơn. Yêu cầu mã đơn hàng dạng số.")
    public String trackOrder(String orderId) {
        log.info("[OrderTools] Tracking order: {}", orderId);
        
        try {
            // Validation with helpful error messages
            if (orderId == null || orderId.isBlank()) {
                return "Vui lòng cung cấp mã đơn hàng. Ví dụ: 12345 hoặc ORD-12345";
            }

            String id = orderId.replaceAll("[^0-9]", "");
            if (id.isBlank()) {
                return "Mã đơn hàng không hợp lệ. Vui lòng nhập mã dạng số (ví dụ: 12345). " +
                       "Bạn có thể tìm mã đơn trong email xác nhận đặt hàng.";
            }

            Optional<Order> orderOpt = orderRepository.findById(id);
            
            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();
                return String.format("""
                    Đơn hàng #%d
                    Trạng thái: %s
                    Ngày đặt: %s
                    Tổng tiền: %,dđ
                    """,
                    order.getOrderId(),
                    translateStatus(order.getStatus()),
                    order.getOrderDate(),
                    order.getTotalAmount() != null ? order.getTotalAmount().longValue() : 0L
                );
            } else {
                return String.format(
                    "Không tìm thấy đơn hàng #%s. Vui lòng kiểm tra lại mã đơn hoặc liên hệ hotline 0397179146.", 
                    orderId
                );
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid order ID format: {}", orderId);
            return "Định dạng mã đơn không hợp lệ. Mã đơn chỉ chứa số, ví dụ: 12345";
        } catch (Exception e) {
            log.error("Error tracking order: {}", e.getMessage(), e);
            return "Không thể tra cứu đơn hàng lúc này. Vui lòng thử lại sau hoặc liên hệ hotline 0397179146.";
        }
    }

    @Tool("Tính phí vận chuyển dựa trên địa chỉ giao hàng")
    public String calculateShipping(String address) {
        log.info("[OrderTools] Calculating shipping for: {}", address);
        
        if (address == null || address.isBlank()) {
            return "Vui lòng cung cấp địa chỉ giao hàng. Ví dụ: 'Quận 1, TP.HCM'";
        }

        try {
            // Simple shipping calculation based on location
            int shippingCost = 30000; // Default
            String deliveryTime = "3-5 ngày";
            
            String lowerAddress = address.toLowerCase();
            if (lowerAddress.contains("hồ chí minh") || lowerAddress.contains("hcm") || 
                lowerAddress.contains("tp.hcm") || lowerAddress.contains("tphcm")) {
                shippingCost = 20000;
                deliveryTime = "1-2 ngày";
            } else if (lowerAddress.contains("hà nội") || lowerAddress.contains("hn")) {
                shippingCost = 25000;
                deliveryTime = "2-3 ngày";
            } else if (lowerAddress.contains("đà nẵng")) {
                shippingCost = 28000;
                deliveryTime = "2-3 ngày";
            }
            
            return String.format("""
                🚚 Phí vận chuyển đến %s:
                - Chi phí: %,dđ
                - Dự kiến giao: %s
                - Miễn phí vận chuyển cho đơn từ 500.000đ
                """, 
                address, shippingCost, deliveryTime);
        } catch (Exception e) {
            log.error("Error calculating shipping: {}", e.getMessage());
            return "❌ Không thể tính phí vận chuyển. Vui lòng thử lại với địa chỉ cụ thể hơn.";
        }
    }

    @Tool("Lấy thông tin trạng thái đơn hàng gần nhất của khách")
    public String getLastOrderStatus(Long userId) {
        log.info("[OrderTools] Getting last order for user: {}", userId);
        
        if (userId == null) {
            return "Không xác định được tài khoản. Vui lòng cung cấp mã đơn hàng cụ thể.";
        }
        
        // TODO: Implement with actual user order lookup
        return "Đơn hàng gần nhất của bạn đang được xử lý. Vui lòng cung cấp mã đơn để tra cứu chi tiết.";
    }

    @Tool("Kiểm tra điều kiện hủy đơn hàng")
    public String checkCancelEligibility(String orderId) {
        log.info("[OrderTools] Checking cancel eligibility for: {}", orderId);
        
        try {
            if (orderId == null || orderId.isBlank()) {
                return "⚠️ Vui lòng cung cấp mã đơn hàng để kiểm tra.";
            }

            String id = orderId.replaceAll("[^0-9]", "");
            if (id.isBlank()) {
                return "⚠️ Mã đơn hàng không hợp lệ. Vui lòng nhập mã dạng số.";
            }

            Optional<Order> orderOpt = orderRepository.findById(id);
            
            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();
                String status = order.getStatus() != null ? order.getStatus().toString().toUpperCase() : "";
                
                if (status.contains("PENDING") || status.contains("PROCESSING")) {
                    return String.format("""
                        Đơn hàng #%s có thể hủy.
                        
                        LƯU Ý: Việc hủy đơn cần được xác nhận bởi nhân viên.
                        Bạn có chắc chắn muốn hủy đơn này không?
                        """, orderId);
                } else if (status.contains("SHIPPED") || status.contains("DELIVERING")) {
                    return String.format("""
                        Đơn hàng #%s đã giao cho đơn vị vận chuyển.
                        
                        Để hủy đơn này, vui lòng:
                        1. Liên hệ hotline: 0397179146
                        2. Hoặc từ chối nhận hàng khi shipper giao
                        """, orderId);
                } else if (status.contains("DELIVERED") || status.contains("COMPLETED")) {
                    return String.format("""
                        Đơn hàng #%s đã hoàn thành, không thể hủy.
                        
                        Nếu bạn muốn đổi/trả hàng, vui lòng sử dụng chức năng yêu cầu đổi trả.
                        """, orderId);
                } else {
                    return "Đơn hàng không thể hủy do trạng thái: " + status;
                }
            }
            
            return String.format("Không tìm thấy đơn hàng #%s. Vui lòng kiểm tra lại mã đơn.", orderId);
        } catch (Exception e) {
            log.error("Error checking cancel eligibility: {}", e.getMessage());
            return "Không thể kiểm tra điều kiện hủy đơn. Vui lòng thử lại sau.";
        }
    }

    @Tool("Kiểm tra điều kiện hoàn tiền đơn hàng")
    public String checkRefundEligibility(String orderId) {
        log.info("[OrderTools] Checking refund eligibility for: {}", orderId);

        try {
            if (orderId == null || orderId.isBlank()) {
                return "Vui lòng cung cấp mã đơn hàng để kiểm tra hoàn tiền.";
            }

            String id = orderId.replaceAll("[^0-9]", "");
            if (id.isBlank()) {
                return "Mã đơn hàng không hợp lệ. Vui lòng nhập mã dạng số.";
            }

            Optional<Order> orderOpt = orderRepository.findById(id);
            if (orderOpt.isEmpty()) {
                return "Không tìm thấy đơn hàng #" + orderId;
            }

            Order order = orderOpt.get();
            OrderStatus status = order.getStatus();
            boolean isPaid = order.isPaid();
            LocalDateTime deliveredAt = order.getDeliveredAt() != null ? order.getDeliveredAt() : order.getOrderDate();
            long daysSinceDelivered = deliveredAt != null ? ChronoUnit.DAYS.between(deliveredAt, LocalDateTime.now()) : Long.MAX_VALUE;

            if (!isPaid) {
                return "Đơn chưa thanh toán nên không cần hoàn tiền. Bạn muốn hủy đơn thay thế không?";
            }

            if (status == OrderStatus.REFUNDED || status == OrderStatus.PARTIALLY_REFUNDED) {
                return "Đơn hàng đã được hoàn tiền trước đó.";
            }

            if (status == OrderStatus.CANCELLED) {
                return "Đơn hàng đã hủy. Nếu đã thanh toán, vui lòng liên hệ để kiểm tra hoàn tiền.";
            }

            if (status == OrderStatus.PENDING || status == OrderStatus.AWAITING_PAYMENT || status == OrderStatus.CONFIRMED || status == OrderStatus.PROCESSING) {
                return String.format(
                        "Đơn #%s có thể yêu cầu hoàn tiền vì chưa giao hàng. Bạn có muốn gửi yêu cầu hoàn tiền không?",
                        orderId
                );
            }

            if (status == OrderStatus.SHIPPED || status == OrderStatus.IN_TRANSIT || status == OrderStatus.OUT_FOR_DELIVERY) {
                return String.format(
                        "Đơn #%s đang giao. Hoàn tiền cần xử lý qua yêu cầu trả hàng sau khi nhận. Bạn muốn tiếp tục không?",
                        orderId
                );
            }

            if ((status == OrderStatus.DELIVERED || status == OrderStatus.COMPLETED) && daysSinceDelivered <= 7) {
                return String.format(
                        "Đơn #%s đủ điều kiện hoàn tiền (đã giao trong %d ngày). Vui lòng cho biết lý do để mình gửi yêu cầu.",
                        orderId,
                        daysSinceDelivered
                );
            }

            if (status == OrderStatus.RETURN_REQUESTED || status == OrderStatus.RETURN_IN_PROGRESS) {
                return String.format(
                        "Đơn #%s đang trong quá trình trả hàng. Hoàn tiền sẽ được xử lý sau khi kiểm tra hàng.",
                        orderId
                );
            }

            return String.format(
                    "Đơn #%s không đủ điều kiện hoàn tiền (trạng thái: %s). Vui lòng liên hệ hotline 0397179146 để được hỗ trợ.",
                    orderId,
                    translateStatus(status)
            );
        } catch (Exception e) {
            log.error("Error checking refund eligibility: {}", e.getMessage());
            return "Không thể kiểm tra điều kiện hoàn tiền. Vui lòng thử lại sau.";
        }
    }

    @Tool("Lấy lịch sử đơn hàng theo email khách hàng")
    public String getOrderHistory(String email) {
        log.info("[OrderTools] Getting order history for email: {}", email);

        if (email == null || email.isBlank()) {
            return "Vui lòng cung cấp email để tra cứu lịch sử đơn.";
        }

        return userRepository.findByEmail(email.trim())
                .map(user -> {
                    var orders = orderRepository.findByUserOrderByCreatedAtDesc(user);
                    if (orders.isEmpty()) {
                        return "Không tìm thấy đơn hàng nào cho email này.";
                    }

                    StringBuilder sb = new StringBuilder("Lịch sử 5 đơn gần nhất:\n\n");
                    orders.stream().limit(5).forEach(o -> sb.append(String.format(
                            "- #%s | %s | Ngày: %s | Tổng: %s\n",
                            o.getOrderId(),
                            translateStatus(o.getStatus()),
                            o.getOrderDate(),
                            formatMoney(o.getFinalAmount() != null ? o.getFinalAmount() : o.getTotalAmount())
                    )));

                    if (orders.size() > 5) {
                        sb.append(String.format("\n... và %d đơn khác", orders.size() - 5));
                    }

                    return sb.toString();
                })
                .orElse("Không tìm thấy tài khoản với email này. Vui lòng kiểm tra lại.");
    }
    
    @Tool("Yêu cầu hủy đơn hàng - cần xác nhận từ nhân viên")
    public String requestCancelOrder(String orderId, String sessionId, String reason) {
        log.info("[OrderTools] Requesting cancel for order: {}, session: {}", orderId, sessionId);
        
        try {
            if (orderId == null || orderId.isBlank()) {
                return "Vui lòng cung cấp mã đơn hàng để hủy.";
            }
            
            String id = orderId.replaceAll("[^0-9]", "");
            Optional<Order> orderOpt = orderRepository.findById(id);
            
            if (orderOpt.isEmpty()) {
                return "Không tìm thấy đơn hàng #" + orderId;
            }
            
            Order order = orderOpt.get();
            String status = order.getStatus() != null ? order.getStatus().toString().toUpperCase() : "";
            
            if (!status.contains("PENDING") && !status.contains("PROCESSING")) {
                return "Đơn hàng không thể hủy do đã ở trạng thái: " + translateStatus(order.getStatus());
            }
            
            // HITL: Queue for approval
            if (humanApprovalService.requiresApproval("CANCEL_ORDER")) {
                String requestId = humanApprovalService.queueForApproval(
                    "CANCEL_ORDER",
                    sessionId,
                    Map.of(
                        "orderId", orderId,
                        "orderAmount", order.getTotalAmount(),
                        "reason", reason != null ? reason : "Không có lý do"
                    )
                );
                
                return String.format("""
                    Yêu cầu hủy đơn #%s đã được ghi nhận.
                    
                    Mã yêu cầu: %s
                    Trạng thái: Đang chờ xác nhận từ nhân viên
                    
                    Bạn sẽ nhận được thông báo khi yêu cầu được xử lý.
                    """, orderId, requestId);
            }
            
            return "Yêu cầu hủy đơn đã được gửi.";
        } catch (Exception e) {
            log.error("Error requesting cancel: {}", e.getMessage());
            return "Không thể gửi yêu cầu hủy đơn. Vui lòng thử lại hoặc liên hệ hotline 0397179146.";
        }
    }
    
    // Helper method to translate status to Vietnamese
    private String translateStatus(Object status) {
        if (status == null) return "Không xác định";
        String s = status.toString().toUpperCase();
        return switch (s) {
            case "PENDING" -> "Chờ xác nhận";
            case "PROCESSING" -> "Đang xử lý";
            case "CONFIRMED" -> "Đã xác nhận";
            case "SHIPPED", "DELIVERING" -> "Đang giao hàng";
            case "DELIVERED" -> "Đã giao";
            case "COMPLETED" -> "Hoàn thành";
            case "CANCELLED" -> "Đã hủy";
            case "REFUNDED" -> "Đã hoàn tiền";
            default -> s;
        };
    }

    private String formatMoney(java.math.BigDecimal amount) {
        if (amount == null) {
            return "N/A";
        }
        return VND_FORMAT.format(amount) + "đ";
    }
}
