package com.example.online_shoe_store.Service;

import com.example.online_shoe_store.Entity.*;
import com.example.online_shoe_store.Repository.*;
import com.example.online_shoe_store.dto.response.CartItemResponse;
import com.example.online_shoe_store.dto.response.ProductVariantDTO; // Import DTO mới
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final UserRepository userRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;
    private final CartRepository cartRepository;

    // --- 1. HÀM XỬ LÝ ẢNH (Fix lỗi ảnh không hiện) ---
    private String getPublicImageUrl(String raw) {
        if (raw == null || raw.isEmpty()) return "/images/no-image.png"; // Ảnh mặc định
        String v = raw.replace("\\", "/").trim();

        if (v.startsWith("http")) return v; // Nếu là link mạng -> giữ nguyên

        // Cắt bỏ phần src/data/... để lấy đúng path public
        if (v.contains("/images/products/")) {
            return "/images/products/" + v.substring(v.lastIndexOf("/images/products/") + 17);
        }
        if (v.startsWith("src/data/")) return "/images/products/" + v.substring(9); // Ví dụ đơn giản

        // Mặc định thêm prefix
        return "/images/products/" + v;
    }

    // --- 2. ADD TO CART (Hỗ trợ userId) ---
    @Transactional
    public void addProductToCartById(String userId, String productId, String size, int quantity) {
        User user = "guest".equals(userId) ? null : userRepository.findById(userId).orElse(null);
        
        // Nếu là khách vãng lai, có thể xử lý qua session/cookie (hiện tại throw error hoặc xử lý guest cart)
        if (user == null) {
            throw new RuntimeException("Vui lòng đăng nhập để thực hiện mua hàng.");
        }

        Cart cart = user.getCart();
        if (cart == null) {
            cart = new Cart();
            cart.setCartId(UUID.randomUUID().toString());
            cart.setUser(user);
            cart.setCartItems(new ArrayList<>());
            cartRepository.save(cart);
        }

        List<ProductVariant> variants = variantRepository.findByProductProductId(productId);
        ProductVariant targetVariant = variants.stream()
                .filter(v -> v.getSize() != null && v.getSize().equalsIgnoreCase(size))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Size '" + size + "' cho sản phẩm này không có sẵn!"));

        Optional<CartItem> existingItemOpt = cart.getCartItems().stream()
                .filter(item -> item.getProductVariant().getVariantId().equals(targetVariant.getVariantId()))
                .findFirst();

        if (existingItemOpt.isPresent()) {
            CartItem existingItem = existingItemOpt.get();
            if (Boolean.FALSE.equals(existingItem.getIsActive())) {
                 existingItem.setQuantity(quantity);
                 existingItem.setIsActive(true);
            } else {
                 existingItem.setQuantity(existingItem.getQuantity() + quantity);
            }
            cartItemRepository.save(existingItem);
        } else {
            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setProductVariant(targetVariant);
            newItem.setQuantity(quantity);
            CartItem savedItem = cartItemRepository.save(newItem);
            cart.getCartItems().add(savedItem);
        }
    }

    // --- 2b. ADD TO CART (Compatibility với username-based API) ---
    @Transactional
    public void addProductToCart(String username, String productId, String size, int quantity) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user: " + username));
        addProductToCartById(user.getUserId(), productId, size, quantity);
    }

    // --- 3. LẤY LIST GIỎ HÀNG (Áp dụng fix ảnh) ---
    @Transactional(readOnly = true)
    public List<CartItemResponse> getCartItemsByUsername(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || user.getCart() == null) return new ArrayList<>();

        return user.getCart().getCartItems().stream()
          .filter(item -> Boolean.TRUE.equals(item.getIsActive())) 
          .map(item -> {
            ProductVariant pv = item.getProductVariant();
            Product p = pv.getProduct();
            
            // Xử lý ảnh tại đây
            String finalImg = getPublicImageUrl(pv.getImageUrl() != null ? pv.getImageUrl() : p.getImageUrl());
            BigDecimal price = p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO;

            return CartItemResponse.builder()
                    .cartItemId(item.getCartItemId())
                    .productId(p.getProductId())
                    .productName(p.getName())
                    .size(pv.getSize())
                    .color(pv.getColor())
                    .imageUrl(finalImg) // <-- Ảnh đã sửa
                    .price(price)
                    .quantity(item.getQuantity())
                    .totalPrice(price.multiply(BigDecimal.valueOf(item.getQuantity())))
                    .build();
        }).collect(Collectors.toList());
    }

    // --- 4. LẤY VARIANT (Fix lỗi phân loại JSON) ---
    public List<ProductVariantDTO> getVariantsByProductId(String productId) {
        // Convert Entity sang DTO để tránh lỗi vòng lặp JSON
        return variantRepository.findByProductProductId(productId).stream()
                .map(v -> new ProductVariantDTO(
                        v.getVariantId(),
                        v.getSize(),
                        v.getColor(),
                        v.getStock()
                ))
                .collect(Collectors.toList());
    }

    // --- Các hàm phụ khác giữ nguyên ---
    @Transactional(readOnly = true)
    public int getCartItemCount(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || user.getCart() == null) return 0;
        return user.getCart().getCartItems().stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsActive()))
                .mapToInt(CartItem::getQuantity).sum();
    }

    public String getUserFullName(String username) {
        return userRepository.findByUsername(username).map(User::getName).orElse("Khách hàng");
    }

    @Transactional
    public void updateItemQuantity(String cartItemId, int qty) {
        cartItemRepository.findById(cartItemId).ifPresent(i -> { i.setQuantity(qty); cartItemRepository.save(i); });
    }

    @Transactional
    public void deleteCartItem(String cartItemId) {
        cartItemRepository.findById(cartItemId).ifPresent(item -> {
            item.setIsActive(false);
            cartItemRepository.save(item);
        });
    }

    @Transactional
    public void updateItemVariant(String cartItemId, String newVariantId) {
        CartItem item = cartItemRepository.findById(cartItemId).orElseThrow();
        ProductVariant newVariant = variantRepository.findById(newVariantId).orElseThrow();

        Optional<CartItem> duplicate = item.getCart().getCartItems().stream()
                .filter(ci -> ci.getProductVariant().getVariantId().equals(newVariantId) && !ci.getCartItemId().equals(cartItemId))
                .findFirst();

        if (duplicate.isPresent()) {
            duplicate.get().setQuantity(duplicate.get().getQuantity() + item.getQuantity());
            cartItemRepository.delete(item);
        } else {
            item.setProductVariant(newVariant);
            cartItemRepository.save(item);
        }
    }

    /**
     * Lấy cart items theo danh sách cart item IDs (dùng cho checkout)
     */
    @Transactional(readOnly = true)
    public List<CartItemResponse> getCartItemsByIds(List<String> cartItemIds) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            return new ArrayList<>();
        }

        return cartItemRepository.findAllById(cartItemIds).stream().map(item -> {
            ProductVariant pv = item.getProductVariant();
            Product p = pv.getProduct();
            BigDecimal price = p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO;
            return CartItemResponse.builder()
                    .cartItemId(item.getCartItemId())
                    .productId(p.getProductId())
                    .productName(p.getName())
                    .size(pv.getSize())
                    .color(pv.getColor())
                    .imageUrl(p.getImageUrl())
                    .price(price)
                    .quantity(item.getQuantity())
                    .totalPrice(price.multiply(BigDecimal.valueOf(item.getQuantity())))
                    .build();
        }).toList();
    }
}