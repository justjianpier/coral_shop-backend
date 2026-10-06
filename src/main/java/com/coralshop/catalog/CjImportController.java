package com.coralshop.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/cj")
public class CjImportController {

    private final CjClient cj;
    private final JdbcTemplate jdbc;

    public CjImportController(CjClient cj, JdbcTemplate jdbc) {
        this.cj = cj;
        this.jdbc = jdbc;
    }

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam @Size(min = 2, max = 100) String keyword,
                                       @RequestParam(defaultValue = "1") @Min(1) @Max(1000) int page) {
        JsonNode data = cj.search(keyword.trim(), page);
        List<Map<String, String>> results = new ArrayList<>();
        for (JsonNode group : data.path("content")) {
            for (JsonNode item : group.path("productList")) {
                if (!item.path("id").asText().isBlank()) {
                    results.add(Map.of("pid", item.path("id").asText(),
                            "name", item.path("nameEn").asText(), "imageUrl", item.path("bigImage").asText(),
                            "supplierPrice", item.path("sellPrice").asText()));
                }
            }
        }
        return Map.of("items", results, "totalPages", data.path("totalPages").asInt(0));
    }

    @GetMapping("/product")
    public Map<String, Object> product(@RequestParam @Pattern(regexp = "[A-Za-z0-9-]{1,80}") String pid) {
        JsonNode data = detail(pid);
        List<Map<String, String>> variants = new ArrayList<>();
        for (JsonNode item : data.path("variants")) {
            variants.add(Map.of("vid", item.path("vid").asText(), "sku", item.path("variantSku").asText(),
                    "optionKey", item.path("variantKey").asText(),
                    "supplierPrice", item.path("variantSellPrice").asText()));
        }
        return Map.of("pid", pid, "name", data.path("productNameEn").asText(),
                "imageUrl", imageUrl(data), "supplierPrice", data.path("sellPrice").asText(),
                "variants", variants, "alreadyImported", exists(pid));
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Long> importProduct(@Valid @RequestBody CjImportRequest request) {
        if (exists(request.pid())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This CJ product is already imported");
        }
        if (jdbc.queryForObject("SELECT count(*) FROM categories WHERE id = ? AND is_active", Integer.class,
                request.categoryId()) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an active category");
        }

        JsonNode data = detail(request.pid());
        String image = imageUrl(data);
        Map<String, String> supplierSkus = new HashMap<>();
        for (JsonNode item : data.path("variants")) {
            supplierSkus.put(item.path("vid").asText(), item.path("variantSku").asText());
        }
        Set<String> selected = new HashSet<>();
        Set<String> combinations = new HashSet<>();
        Set<String> skus = new HashSet<>();
        for (CjImportRequest.Variant variant : request.variants()) {
            String sku = "CJ-" + supplierSkus.getOrDefault(variant.vid(), "");
            if (!selected.add(variant.vid()) || !supplierSkus.containsKey(variant.vid())
                    || supplierSkus.get(variant.vid()).isBlank() || sku.length() > 80 || !skus.add(sku)
                    || !combinations.add(variant.sizeId() + ":" + variant.colorId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Select distinct CJ variants with unique size/color and valid SKUs");
            }
            if (jdbc.queryForObject("SELECT count(*) FROM sizes WHERE id = ?", Integer.class, variant.sizeId()) == 0
                    || jdbc.queryForObject("SELECT count(*) FROM colors WHERE id = ?", Integer.class,
                    variant.colorId()) == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a valid size and color");
            }
        }

        try {
            GeneratedKeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO products (name, description, base_price, category_id, is_active, cj_product_id)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, new String[] {"id"});
                statement.setString(1, request.name().trim());
                statement.setString(2, request.description());
                statement.setBigDecimal(3, request.basePrice());
                statement.setLong(4, request.categoryId());
                statement.setBoolean(5, request.isActive());
                statement.setString(6, request.pid());
                return statement;
            }, keys);
            long productId = keys.getKey().longValue();
            jdbc.update("INSERT INTO product_images (product_id, image_url, alt_text, is_primary) VALUES (?, ?, ?, true)",
                    productId, image, request.name().trim());
            for (CjImportRequest.Variant variant : request.variants()) {
                jdbc.update("""
                        INSERT INTO product_variants (product_id, size_id, color_id, sku, stock, cj_variant_id)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, productId, variant.sizeId(), variant.colorId(),
                        "CJ-" + supplierSkus.get(variant.vid()), variant.stock(), variant.vid());
            }
            return Map.of("id", productId);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "CJ product or variant already imported, or SKU is in use", exception);
        }
    }

    private JsonNode detail(String pid) {
        JsonNode data = cj.product(pid);
        if (!pid.equalsIgnoreCase(data.path("pid").asText())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CJ product not found");
        }
        return data;
    }

    private String imageUrl(JsonNode data) {
        String image = data.path("bigImage").asText();
        if (!image.startsWith("https://")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "CJ product has no secure main image");
        }
        return image;
    }

    private boolean exists(String pid) {
        return jdbc.queryForObject("SELECT count(*) FROM products WHERE cj_product_id = ?", Integer.class, pid) > 0;
    }
}
