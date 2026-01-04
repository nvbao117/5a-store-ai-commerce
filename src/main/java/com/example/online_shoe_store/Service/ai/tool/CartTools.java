package com.example.online_shoe_store.Service.ai.tool;

import com.example.online_shoe_store.Service.CartService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Cart Tools - Công cụ cho phép Agent thao tác với giỏ hàng của người dùng
 * userId được truyền từ context trong prompt của Agent
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CartTools {

    private final CartService cartService;

    @Tool(name = "addToCart", value = """
        Thêm một sản phẩm vào giỏ hàng của người dùng. 
        Sử dụng khi khách hàng biểu thị ý muốn mua hoặc thích một sản phẩm cụ thể.
        Nếu khách hàng chưa chọn size, hãy yêu cầu họ chọn size trước hoặc lấy size được đề cập gần nhất.
        Lấy userId từ context đã được cung cấp trong prompt.
        """)
    public String addToCart(
            @P("User ID của khách hàng (lấy từ context)") String userId,
            @P("ID của sản phẩm (productId)") String productId,
            @P("Size giày (ví dụ: 42, 38, L, M)") String size,
            @P("Số lượng sản phẩm (mặc định là 1)") Integer quantity
    ) {
        if (userId == null || userId.isBlank() || "guest".equals(userId)) {
            return "Vui lòng đăng nhập để có thể thêm sản phẩm vào giỏ hàng.";
        }

        int qty = (quantity != null && quantity > 0) ? quantity : 1;
        
        log.info("[CartTools] Adding product {} (size {}) to cart for user {}", productId, size, userId);
        
        try {
            cartService.addProductToCartById(userId, productId, size, qty);
            return String.format("Thành công! Đã thêm sản phẩm vào giỏ hàng với size %s.", size);
        } catch (Exception e) {
            log.error("[CartTools] Error adding to cart: {}", e.getMessage());
            return "Lỗi: " + e.getMessage();
        }
    }
}
