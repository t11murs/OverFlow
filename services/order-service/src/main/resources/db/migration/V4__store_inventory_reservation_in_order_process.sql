ALTER TABLE order_process
    ADD COLUMN inventory_reservation_id UUID,
    ADD COLUMN inventory_reservation_expires_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_order_process_inventory_reservation CHECK (
        (inventory_reservation_id IS NULL AND inventory_reservation_expires_at IS NULL)
        OR (inventory_reservation_id IS NOT NULL AND inventory_reservation_expires_at IS NOT NULL)
    );
