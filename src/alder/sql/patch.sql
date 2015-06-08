-- name: do-create-patch<!
INSERT INTO patch (short_id, patch_data) VALUES (:short_id, :patch_data);


-- name: do-save-patch!
UPDATE patch SET patch_data = :patch_data WHERE short_id = :short_id;


-- name: do-get-patch
SELECT patch_data::varchar, created_at, updated_at FROM patch WHERE short_id = :short_id;
