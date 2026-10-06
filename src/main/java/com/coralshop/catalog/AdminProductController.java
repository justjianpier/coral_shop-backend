package com.coralshop.catalog;

import jakarta.validation.Valid;
import java.sql.PreparedStatement;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
public class AdminProductController {

    private final JdbcTemplate jdbc;

    public AdminProductController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/catalog-options")
    public Map<String, List<Map<String, Object>>> options() {
        List<Map<String, Object>> sizes = jdbc.query("SELECT id, name FROM sizes ORDER BY sort_order, id",
                (rs, row) -> Map.of("id", rs.getLong("id"), "name", rs.getString("name")));
        List<Map<String, Object>> colors = jdbc.query("SELECT id, name FROM colors ORDER BY name",
                (rs, row) -> Map.of("id", rs.getLong("id"), "name", rs.getString("name")));
        return Map.of("sizes", sizes, "colors", colors);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Long> create(@Valid @RequestBody CreateProductRequest request) {
        if (jdbc.queryForObject("SELECT count(*) FROM categories WHERE id = ? AND is_active", Integer.class,
                request.categoryId()) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an active category");
        }

        Set<String> combinations = new HashSet<>();
        Set<String> skus = new HashSet<>();
        for (CreateProductRequest.Variant variant : request.variants()) {
            if (!combinations.add(variant.sizeId() + ":" + variant.colorId())
                    || !skus.add(variant.sku().trim())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate variant or SKU");
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
                        INSERT INTO products (name, description, base_price, category_id, is_active)
                        VALUES (?, ?, ?, ?, ?)
                        """, new String[] {"id"});
                statement.setString(1, request.name().trim());
                statement.setString(2, request.description());
                statement.setBigDecimal(3, request.basePrice());
                statement.setLong(4, request.categoryId());
                statement.setBoolean(5, request.isActive());
                return statement;
            }, keys);
            Long id = keys.getKey().longValue();

            jdbc.update("INSERT INTO product_images (product_id, image_url, alt_text, is_primary) VALUES (?, ?, ?, true)",
                    id, request.imageUrl().trim(), request.name().trim());
            for (CreateProductRequest.Variant variant : request.variants()) {
                jdbc.update("""
                        INSERT INTO product_variants (product_id, size_id, color_id, sku, stock)
                        VALUES (?, ?, ?, ?, ?)
                        """, id, variant.sizeId(), variant.colorId(), variant.sku().trim(), variant.stock());
            }
            return Map.of("id", id);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists or catalog data changed", exception);
        }
    }
}
