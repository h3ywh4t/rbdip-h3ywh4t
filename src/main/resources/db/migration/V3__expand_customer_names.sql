ALTER TABLE customers
    ADD COLUMN first_name VARCHAR(255),
    ADD COLUMN last_name VARCHAR(255);

ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_full_name VARCHAR(255);

UPDATE orders o SET customer_full_name = c.full_name
FROM customers c
WHERE o.customer_id = c.id AND o.customer_full_name IS DISTINCT FROM c.full_name;

ALTER TABLE orders ALTER COLUMN customer_full_name SET NOT NULL;

CREATE OR REPLACE FUNCTION sync_order_customer_name() RETURNS TRIGGER AS $$
BEGIN
    SELECT full_name INTO NEW.customer_full_name FROM customers WHERE id = NEW.customer_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER orders_customer_name_sync
BEFORE INSERT OR UPDATE OF customer_id ON orders
FOR EACH ROW EXECUTE FUNCTION sync_order_customer_name();

CREATE OR REPLACE FUNCTION sync_customer_order_names() RETURNS TRIGGER AS $$
BEGIN
    UPDATE orders SET customer_full_name = NEW.full_name WHERE customer_id = NEW.id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER customers_order_names_sync
AFTER UPDATE ON customers
FOR EACH ROW WHEN (OLD.full_name IS DISTINCT FROM NEW.full_name)
EXECUTE FUNCTION sync_customer_order_names();

CREATE FUNCTION sync_customer_name() RETURNS TRIGGER AS $$
DECLARE
    separator INTEGER;
BEGIN
    IF NEW.first_name IS NULL OR (
        TG_OP = 'UPDATE'
        AND NEW.full_name IS DISTINCT FROM OLD.full_name
        AND NEW.first_name IS NOT DISTINCT FROM OLD.first_name
        AND NEW.last_name IS NOT DISTINCT FROM OLD.last_name
    ) THEN
        separator := strpos(NEW.full_name, ' ');
        IF separator = 0 THEN
            NEW.first_name := NEW.full_name;
            NEW.last_name := NULL;
        ELSE
            NEW.first_name := left(NEW.full_name, separator - 1);
            NEW.last_name := substring(NEW.full_name FROM separator + 1);
        END IF;
    ELSE
        NEW.full_name := NEW.first_name
            || CASE WHEN NEW.last_name IS NULL THEN '' ELSE ' ' || NEW.last_name END;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER customers_name_sync
BEFORE INSERT OR UPDATE OF full_name, first_name, last_name ON customers
FOR EACH ROW EXECUTE FUNCTION sync_customer_name();
