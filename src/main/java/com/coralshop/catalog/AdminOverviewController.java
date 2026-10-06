package com.coralshop.catalog;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class AdminOverviewController {

    private final JdbcTemplate jdbc;

    public AdminOverviewController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/overview")
    public Overview overview() {
        long users = jdbc.queryForObject("SELECT count(*) FROM users", Long.class);
        long products = jdbc.queryForObject("SELECT count(*) FROM products", Long.class);
        long orders = jdbc.queryForObject("SELECT count(*) FROM orders", Long.class);
        BigDecimal revenue = jdbc.queryForObject(
                "SELECT COALESCE(SUM(total_amount), 0) FROM orders WHERE status = 'DELIVERED'", BigDecimal.class);
        List<RecentOrder> recentOrders = jdbc.query("""
                SELECT o.id, u.username, o.total_amount, o.status, o.created_at
                FROM orders o JOIN users u ON u.id = o.user_id
                ORDER BY o.created_at DESC LIMIT 5
                """, (rs, row) -> new RecentOrder(rs.getLong("id"), rs.getString("username"),
                rs.getBigDecimal("total_amount"), rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class)));
        return new Overview(users, products, orders, revenue, recentOrders);
    }

    public record Overview(long totalUsers, long totalProducts, long totalOrders,
                           BigDecimal totalRevenue, List<RecentOrder> recentOrders) {
    }

    public record RecentOrder(long id, String username, BigDecimal totalAmount,
                              String status, OffsetDateTime createdAt) {
    }
}
