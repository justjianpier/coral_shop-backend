package com.coralshop.catalog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private static final String PRODUCTS_SQL = """
            SELECT p.id, p.name, p.description, p.base_price, p.category_id,
                   c.name AS category_name, b.name AS brand_name,
                   (SELECT image_url FROM product_images i WHERE i.product_id = p.id
                    ORDER BY i.is_primary DESC, i.sort_order, i.id LIMIT 1) AS image_url,
                   COALESCE((SELECT SUM(v.stock) FROM product_variants v
                             WHERE v.product_id = p.id AND v.is_active), 0) AS total_stock,
                   p.is_active
            FROM products p
            JOIN categories c ON c.id = p.category_id
            LEFT JOIN brands b ON b.id = p.brand_id
            WHERE 1 = 1
            """;

    private static final RowMapper<ProductView> PRODUCT_MAPPER = (rs, row) ->
            new ProductView(rs.getLong("id"), rs.getString("name"), rs.getString("description"),
                    rs.getBigDecimal("base_price"), rs.getLong("category_id"),
                    rs.getString("category_name"), rs.getString("brand_name"),
                    rs.getString("image_url"), rs.getInt("total_stock"), rs.getBoolean("is_active"), List.of());

    private final JdbcTemplate jdbc;

    public CatalogController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/products")
    public List<ProductView> products() {
        return jdbc.query(PRODUCTS_SQL + " AND p.is_active AND c.is_active ORDER BY p.id DESC", PRODUCT_MAPPER);
    }

    @GetMapping("/admin/products")
    public List<ProductView> adminProducts() {
        return jdbc.query(PRODUCTS_SQL + " ORDER BY p.id DESC", PRODUCT_MAPPER);
    }

    @GetMapping("/products/{id}")
    public ProductView product(@PathVariable Long id) {
        List<ProductView> matches = jdbc.query(PRODUCTS_SQL + " AND p.is_active AND c.is_active AND p.id = ?",
                PRODUCT_MAPPER, id);
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        ProductView product = matches.getFirst();
        List<VariantView> variants = jdbc.query("""
                SELECT v.id, v.sku, s.name AS size, co.name AS color, v.stock
                FROM product_variants v
                JOIN sizes s ON s.id = v.size_id
                JOIN colors co ON co.id = v.color_id
                WHERE v.product_id = ? AND v.is_active
                ORDER BY s.sort_order, co.name, v.id
                """, (rs, row) -> mapVariant(rs), id);
        return new ProductView(product.id(), product.name(), product.description(), product.basePrice(),
                product.categoryId(), product.categoryName(), product.brandName(), product.imageUrl(),
                product.totalStock(), product.isActive(), variants);
    }

    @GetMapping("/categories")
    public List<Map<String, Object>> categories() {
        return jdbc.query("SELECT id, name, description FROM categories WHERE is_active ORDER BY name",
                (rs, row) -> Map.of("id", rs.getLong("id"), "name", rs.getString("name"),
                        "description", rs.getString("description") == null ? "" : rs.getString("description")));
    }

    @GetMapping("/brands")
    public List<Map<String, Object>> brands() {
        return jdbc.query("SELECT id, name FROM brands WHERE is_active ORDER BY name",
                (rs, row) -> Map.of("id", rs.getLong("id"), "name", rs.getString("name")));
    }

    private VariantView mapVariant(ResultSet rs) throws SQLException {
        return new VariantView(rs.getLong("id"), rs.getString("sku"), rs.getString("size"),
                rs.getString("color"), rs.getInt("stock"));
    }
}
