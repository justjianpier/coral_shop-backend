package com.coralshop.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record CreateProductRequest(
        @NotBlank @Size(max = 180) String name,
        String description,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal basePrice,
        @NotNull @Positive Long categoryId,
        @NotBlank @Pattern(regexp = "https?://.+", message = "Image must be an HTTP(S) URL") String imageUrl,
        boolean isActive,
        @NotEmpty @Valid List<Variant> variants
) {
    public record Variant(
            @NotNull @Positive Long sizeId,
            @NotNull @Positive Long colorId,
            @NotBlank @Size(max = 80) String sku,
            @NotNull @PositiveOrZero Integer stock
    ) {
    }
}
