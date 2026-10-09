DROP TRIGGER customers_name_sync ON customers;
DROP TRIGGER customers_order_names_sync ON customers;
DROP TRIGGER orders_customer_name_sync ON orders;

DROP FUNCTION sync_customer_name();
DROP FUNCTION sync_customer_order_names();
DROP FUNCTION sync_order_customer_name();

ALTER TABLE customers
    DROP CONSTRAINT customers_contact_key,
    DROP COLUMN full_name;

ALTER TABLE orders DROP COLUMN customer_full_name;
