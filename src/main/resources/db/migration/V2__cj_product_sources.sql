ALTER TABLE products ADD COLUMN cj_product_id VARCHAR(80);
CREATE UNIQUE INDEX products_cj_product_id_unique ON products (cj_product_id) WHERE cj_product_id IS NOT NULL;

ALTER TABLE product_variants ADD COLUMN cj_variant_id VARCHAR(80);
CREATE UNIQUE INDEX variants_cj_variant_id_unique ON product_variants (cj_variant_id) WHERE cj_variant_id IS NOT NULL;
