UPDATE customers
SET first_name = split_part(full_name, ' ', 1),
    last_name = CASE WHEN strpos(full_name, ' ') = 0 THEN NULL
                    ELSE substring(full_name FROM strpos(full_name, ' ') + 1) END
WHERE first_name IS NULL;

ALTER TABLE customers
    ALTER COLUMN first_name SET NOT NULL,
    ADD CONSTRAINT customers_name_length CHECK (
        char_length(first_name)
        + CASE WHEN last_name IS NULL THEN 0 ELSE 1 + char_length(last_name) END <= 255
    ),
    ADD CONSTRAINT customers_split_contact_key
        UNIQUE NULLS NOT DISTINCT (first_name, last_name, address, phone);
