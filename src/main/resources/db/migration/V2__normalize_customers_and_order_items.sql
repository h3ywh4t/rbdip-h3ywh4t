CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    phone VARCHAR(50),
    CONSTRAINT customers_contact_key UNIQUE NULLS NOT DISTINCT (full_name, address, phone)
);

INSERT INTO customers (full_name, address, phone)
SELECT DISTINCT customer_full_name, customer_address, customer_phone
FROM orders;

ALTER TABLE orders ADD COLUMN customer_id BIGINT;

UPDATE orders o
SET customer_id = c.id
FROM customers c
WHERE c.full_name = o.customer_full_name
  AND c.address IS NOT DISTINCT FROM o.customer_address
  AND c.phone IS NOT DISTINCT FROM o.customer_phone;

ALTER TABLE orders
    ALTER COLUMN customer_id SET NOT NULL,
    ADD CONSTRAINT orders_customer_fk FOREIGN KEY (customer_id) REFERENCES customers(id);

CREATE INDEX orders_customer_id_idx ON orders(customer_id);

INSERT INTO products (name, price)
SELECT DISTINCT i.product_name, i.product_price
FROM order_items i
WHERE NOT EXISTS (
    SELECT 1 FROM products p
    WHERE p.name = i.product_name AND p.price = i.product_price
);

ALTER TABLE order_items ADD COLUMN product_id BIGINT;

UPDATE order_items i
SET product_id = (
    SELECT MIN(p.id) FROM products p
    WHERE p.name = i.product_name AND p.price = i.product_price
);

ALTER TABLE order_items
    ALTER COLUMN product_id SET NOT NULL,
    ADD CONSTRAINT order_items_product_fk FOREIGN KEY (product_id) REFERENCES products(id);

CREATE INDEX order_items_product_id_idx ON order_items(product_id);

ALTER TABLE orders
    DROP COLUMN customer_address,
    DROP COLUMN customer_phone;

ALTER TABLE order_items
    DROP COLUMN product_name,
    DROP COLUMN product_price;

CREATE FUNCTION sync_order_customer_name() RETURNS TRIGGER AS $$
BEGIN
    SELECT full_name INTO NEW.customer_full_name FROM customers WHERE id = NEW.customer_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER orders_customer_name_sync
BEFORE INSERT OR UPDATE OF customer_id ON orders
FOR EACH ROW EXECUTE FUNCTION sync_order_customer_name();

CREATE FUNCTION sync_customer_order_names() RETURNS TRIGGER AS $$
BEGIN
    UPDATE orders SET customer_full_name = NEW.full_name WHERE customer_id = NEW.id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER customers_order_names_sync
AFTER UPDATE ON customers
FOR EACH ROW WHEN (OLD.full_name IS DISTINCT FROM NEW.full_name)
EXECUTE FUNCTION sync_customer_order_names();
