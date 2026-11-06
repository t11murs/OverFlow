ALTER TABLE customer_order
    DROP CONSTRAINT ck_customer_order_status,
    ADD CONSTRAINT ck_customer_order_status CHECK (status IN (
        'CREATED', 'RESERVING_INVENTORY', 'AWAITING_PAYMENT', 'CONFIRMED',
        'CANCELLATION_PENDING', 'COMPENSATION_FAILED', 'CANCELLED'
    ));

ALTER TABLE seller_order
    DROP CONSTRAINT ck_seller_order_status,
    ADD CONSTRAINT ck_seller_order_status CHECK (status IN (
        'CREATED', 'ALLOCATED', 'CONFIRMED', 'COMPENSATION_FAILED', 'CANCELLED'
    ));

ALTER TABLE fulfillment_group
    DROP CONSTRAINT ck_fulfillment_group_status,
    ADD CONSTRAINT ck_fulfillment_group_status CHECK (status IN (
        'ALLOCATED', 'CONFIRMED', 'CANCELLED'
    ));
