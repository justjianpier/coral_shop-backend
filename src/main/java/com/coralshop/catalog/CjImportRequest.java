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

public record CjImportRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{1,80}") String pid,
        @NotBlank @Size(max = 180) String name,
        @Size(max = 4000) String description,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal basePrice,
        @NotNull @Positive Long categoryId,
        boolean isActive,
        @NotEmpty @Size(max = 30) @Valid List<Variant> variants
) {
    public record Variant(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{1,80}") String vid,
            @NotNull @Positive Long sizeId,
            @NotNull @Positive Long colorId,
            @NotNull @PositiveOrZero Integer stock
    ) {
    }
}
