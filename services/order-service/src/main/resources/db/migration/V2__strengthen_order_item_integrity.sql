ALTER TABLE seller_order
    ADD CONSTRAINT ux_seller_order_id_order UNIQUE (id, order_id),
    ADD CONSTRAINT ux_seller_order_id_seller UNIQUE (id, seller_id);

ALTER TABLE order_item
    ADD CONSTRAINT fk_order_item_seller_order_order
        FOREIGN KEY (seller_order_id, order_id)
        REFERENCES seller_order (id, order_id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT fk_order_item_seller_order_seller
        FOREIGN KEY (seller_order_id, seller_id)
        REFERENCES seller_order (id, seller_id)
        ON DELETE RESTRICT;
