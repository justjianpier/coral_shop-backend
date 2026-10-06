package com.coralshop.catalog;

import java.math.BigDecimal;
import java.util.List;

public record ProductView(
        Long id,
        String name,
        String description,
        BigDecimal basePrice,
        Long categoryId,
        String categoryName,
        String brandName,
        String imageUrl,
        int totalStock,
        boolean isActive,
        List<VariantView> variants
) {
}
