ALTER TABLE refresh_token
    ADD COLUMN family_id UUID;

WITH RECURSIVE token_family (id, family_id) AS (
    SELECT token.id, token.id
    FROM refresh_token token
    WHERE NOT EXISTS (
        SELECT 1
        FROM refresh_token parent
        WHERE parent.replaced_by_token_id = token.id
    )

    UNION ALL

    SELECT child.id, token_family.family_id
    FROM token_family
    JOIN refresh_token parent ON parent.id = token_family.id
    JOIN refresh_token child ON child.id = parent.replaced_by_token_id
)
UPDATE refresh_token token
SET family_id = token_family.family_id
FROM token_family
WHERE token.id = token_family.id;

UPDATE refresh_token
SET family_id = id
WHERE family_id IS NULL;

ALTER TABLE refresh_token
    ALTER COLUMN family_id SET NOT NULL;

CREATE INDEX ix_refresh_token_family_id ON refresh_token (family_id);
