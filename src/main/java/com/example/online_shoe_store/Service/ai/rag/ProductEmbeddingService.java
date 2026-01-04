package com.example.online_shoe_store.Service.ai.rag;

import com.example.online_shoe_store.Entity.Product;
import com.example.online_shoe_store.Repository.ProductRepository;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Service để embedding tất cả sản phẩm từ database vào ChromaDB
 * Mỗi sản phẩm được chuyển thành text segment và lưu vào vector store
 */
@Service
@Slf4j
public class ProductEmbeddingService {

    private final EmbeddingStore<TextSegment> productEmbeddingStore;
    private final EmbeddingModel embeddingModel;
    private final ProductRepository productRepository;

    @Value("${search.agent.embedding.force-reingest:false}")
    private Boolean forceReingest;

    private static final NumberFormat VND_FORMAT = NumberFormat.getInstance(new Locale("vi", "VN"));

    public ProductEmbeddingService(
            @Qualifier("productEmbeddingStore") EmbeddingStore<TextSegment> productEmbeddingStore,
            EmbeddingModel embeddingModel,
            ProductRepository productRepository
    ) {
        this.productEmbeddingStore = productEmbeddingStore;
        this.embeddingModel = embeddingModel;
        this.productRepository = productRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void onApplicationReady() {
        if (Boolean.TRUE.equals(forceReingest)) {
            log.info("[ProductRAG] Force re-ingesting all products...");
            ingestAllProducts();
        } else {
            log.info("[ProductRAG] Product ingestion skipped (force-reingest=false)");
        }
    }

    /**
     * Ingest tất cả sản phẩm active từ database vào vector store
     */
    public void ingestAllProducts() {
        try {
            log.info("[ProductRAG] Starting product ingestion...");
            
            // Lấy tất cả sản phẩm ACTIVE và fetch brand/category để tránh LazyInitializationException
            List<Product> products = productRepository.findAllActiveWithBrandCategory();

            if (products.isEmpty()) {
                log.warn("[ProductRAG] No active products found!");
                return;
            }

            log.info("[ProductRAG] Found {} active products. Starting embedding...", products.size());

            int successCount = 0;
            int errorCount = 0;

            for (Product product : products) {
                try {
                    ingestProduct(product);
                    successCount++;
                } catch (Exception e) {
                    log.error("[ProductRAG] Error embedding product {}: {}", product.getProductId(), e.getMessage());
                    errorCount++;
                }
            }

            log.info("[ProductRAG] Ingestion completed! Success: {}, Errors: {}", successCount, errorCount);

        } catch (Exception e) {
            log.error("[ProductRAG] Error during ingestion: {}", e.getMessage(), e);
        }
    }

    public void ingestProduct(Product product) {
        // Build text content cho embedding
        String textContent = buildProductText(product);
        
        // Build metadata
        Metadata metadata = buildProductMetadata(product);
        
        // Tạo text segment
        TextSegment segment = TextSegment.from(textContent, metadata);
        
        // Embed và store
        Embedding embedding = embeddingModel.embed(segment).content();
        productEmbeddingStore.add(embedding, segment);
        
        log.debug("[ProductRAG] Embedded product: {} - {}", product.getProductId(), product.getName());
    }

    /**
     * Cập nhật embedding khi sản phẩm được update
     */
    public void updateProductEmbedding(Product product) {
        // Xóa embedding cũ nếu có (theo id)
        // ChromaDB sẽ tự động handle việc này nếu dùng same id
        ingestProduct(product);
        log.info("[ProductRAG] Updated embedding for product: {}", product.getProductId());
    }

    /**
     * Build text content cho embedding
     * Format: Tên sản phẩm. Thương hiệu X. Danh mục Y. Màu Z. Giá N đồng. Mô tả...
     */
    private String buildProductText(Product product) {
        StringBuilder sb = new StringBuilder();
        
        // Tên sản phẩm
        sb.append(product.getName());
        
        // Thương hiệu
        if (product.getBrand() != null && product.getBrand().getName() != null) {
            sb.append(". Hãng: ").append(product.getBrand().getName());
        }
        
        // Danh mục
        if (product.getCategory() != null && product.getCategory().getName() != null) {
            sb.append(". Loại: ").append(product.getCategory().getName());
        }
        
        // Giá
        if (product.getPrice() != null) {
            sb.append(". Giá: ").append(formatPrice(product.getPrice()));
        }
        
        // Mô tả (giới hạn 300 ký tự)
        if (product.getDescription() != null && !product.getDescription().isBlank()) {
            String desc = cleanText(product.getDescription());
            if (desc.length() > 300) {
                desc = desc.substring(0, 300);
            }
            sb.append(". ").append(desc);
        }
        
        return sb.toString();
    }

    /**
     * Build metadata cho text segment
     */
    private Metadata buildProductMetadata(Product product) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("id", product.getProductId());
        metadata.put("name", product.getName());
        metadata.put("price", product.getPrice() != null ? product.getPrice().doubleValue() : 0.0);
        metadata.put("status", product.getStatus() != null ? product.getStatus().name() : "ACTIVE");

        if (product.getImageUrl() != null) {
            metadata.put("imageUrl", normalizeImageUrl(product.getImageUrl()));
        }

        if (product.getBrand() != null && product.getBrand().getName() != null) {
            metadata.put("brandName", product.getBrand().getName());
            metadata.put("brandId", product.getBrand().getBrandId());
        }

        if (product.getCategory() != null && product.getCategory().getName() != null) {
            metadata.put("categoryName", product.getCategory().getName());
            metadata.put("categoryId", product.getCategory().getCategoryId());
        }

        if (product.getDescription() != null) {
            String desc = cleanText(product.getDescription());
            if (desc.length() > 500) {
                desc = desc.substring(0, 500);
            }
            metadata.put("description", desc);
        }

        return Metadata.from(metadata);
    }

    private String formatPrice(BigDecimal price) {
        if (price == null) return "Liên hệ";
        return VND_FORMAT.format(price) + "đ";
    }

    private String cleanText(String text) {
        if (text == null) return "";
        return text.replaceAll("[\\r\\n\\t]+", " ")
                   .replaceAll("\\s+", " ")
                   .trim();
    }

    private String normalizeImageUrl(String raw) {
        if (raw == null) return null;
        String v = raw.replace("\\", "/").trim();
        
        String p1 = "/src/data/images/products/";
        if (v.startsWith(p1)) {
            return "/images/products/" + v.substring(p1.length());
        }
        
        String p2 = "src/data/images/products/";
        if (v.startsWith(p2)) {
            return "/images/products/" + v.substring(p2.length());
        }
        
        if (v.startsWith("/images/products/")) {
            return v;
        }
        
        if (!v.startsWith("/") && !v.startsWith("http://") && !v.startsWith("https://")) {
            return "/images/products/" + v;
        }
        
        return v;
    }

    /**
     * Lấy số lượng embeddings hiện có trong store
     */
    public int getEmbeddingCount() {
        // ChromaDB không có method count() trực tiếp trong LangChain4j
        // Nên return -1 để indicate unknown
        return -1;
    }
}
