-- =============================================================================
-- V2 — Seed data so reviewers can exercise every flow immediately.
--
-- Logins (password shown; hashes are BCrypt):
--   admin@oms.local    / Admin123!      (ADMIN)
--   staff@oms.local    / Staff123!      (WAREHOUSE_STAFF)
--   customer@oms.local / Customer123!   (CUSTOMER)
--
-- Foreign keys are resolved via natural-key subqueries rather than literal ids so
-- this runs cleanly on both PostgreSQL and H2 without touching identity sequences.
-- =============================================================================

-- Users -----------------------------------------------------------------------
INSERT INTO users (created_at, updated_at, email, password_hash, display_name, role) VALUES
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin@oms.local',
     '$2a$10$lQklhE9dPsli0K4xOHn6luKNNNYuhGAg2lCvo4VNTdTPl6L6RhPJm', 'Seed Admin', 'ADMIN'),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'staff@oms.local',
     '$2a$10$H5HYGD4nr9XbvSj2g85g3e43Ckh6qubufxnaRe.x5J37EborU0AV.', 'Seed Staff', 'WAREHOUSE_STAFF'),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'customer@oms.local',
     '$2a$10$9JCvZJ8lO3vybTWotV.Y4O/eH38tnAMj/pNEZsv5KQflRYSH676ry', 'Seed Customer', 'CUSTOMER');

-- Tax rates (single store region: US) -----------------------------------------
INSERT INTO tax_rates (created_at, updated_at, tax_category, region, rate_percent) VALUES
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'STANDARD', 'US', 8.250),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'REDUCED',  'US', 2.000),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'NONE',     'US', 0.000);

-- Categories (Accessories nests under Electronics) ----------------------------
INSERT INTO categories (created_at, updated_at, name, parent_id) VALUES
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'Electronics', NULL),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'Books', NULL);
INSERT INTO categories (created_at, updated_at, name, parent_id) VALUES
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'Accessories',
     (SELECT id FROM categories WHERE name = 'Electronics'));

-- Products --------------------------------------------------------------------
INSERT INTO products (created_at, updated_at, sku, name, description, base_price, tax_category, active, category_id) VALUES
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SKU-HEADPHONES', 'Wireless Headphones',
     'Over-ear noise-cancelling headphones', 199.99, 'STANDARD', TRUE,
     (SELECT id FROM categories WHERE name = 'Electronics')),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SKU-USB-CABLE', 'USB-C Cable',
     'Braided 2m USB-C charging cable', 12.50, 'STANDARD', TRUE,
     (SELECT id FROM categories WHERE name = 'Accessories')),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SKU-NOVEL', 'Paperback Novel',
     'Bestselling fiction paperback', 9.99, 'REDUCED', TRUE,
     (SELECT id FROM categories WHERE name = 'Books')),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SKU-GIFTCARD', 'Gift Card',
     'Digital gift card', 25.00, 'NONE', TRUE, NULL);

-- Warehouses ------------------------------------------------------------------
INSERT INTO warehouses (created_at, updated_at, code, name, region, priority) VALUES
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WH-EAST', 'East Coast DC', 'US-EAST', 1),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WH-WEST', 'West Coast DC', 'US-WEST', 2);

-- Stock levels (headphones split across both warehouses to demo allocation) ----
INSERT INTO stock_levels (created_at, updated_at, product_id, warehouse_id, quantity_on_hand, quantity_reserved) VALUES
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
     (SELECT id FROM products WHERE sku = 'SKU-HEADPHONES'), (SELECT id FROM warehouses WHERE code = 'WH-EAST'), 5, 0),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
     (SELECT id FROM products WHERE sku = 'SKU-HEADPHONES'), (SELECT id FROM warehouses WHERE code = 'WH-WEST'), 3, 0),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
     (SELECT id FROM products WHERE sku = 'SKU-USB-CABLE'), (SELECT id FROM warehouses WHERE code = 'WH-EAST'), 100, 0),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
     (SELECT id FROM products WHERE sku = 'SKU-NOVEL'), (SELECT id FROM warehouses WHERE code = 'WH-WEST'), 40, 0),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
     (SELECT id FROM products WHERE sku = 'SKU-GIFTCARD'), (SELECT id FROM warehouses WHERE code = 'WH-EAST'), 999, 0);

-- Coupon ----------------------------------------------------------------------
INSERT INTO coupons (created_at, updated_at, code, type, discount_value, min_order_amount, valid_from, valid_to, max_redemptions, times_redeemed, active) VALUES
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WELCOME10', 'PERCENT', 10.00, 20.00, NULL, NULL, NULL, 0, TRUE),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SAVE5', 'FIXED', 5.00, 0.00, NULL, NULL, 100, 0, TRUE);
