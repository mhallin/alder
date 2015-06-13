-- name: do-create-patch<!
INSERT INTO patch (short_id, patch_data) VALUES (:short_id, :patch_data);


-- name: do-save-patch!
UPDATE patch SET patch_data = :patch_data WHERE short_id = :short_id;


-- name: do-update-visited-at!
UPDATE patch SET last_visited_at = NOW() WHERE short_id = :short_id;


-- name: do-get-patch
SELECT short_id, patch_data::varchar, created_at, updated_at, read_only
    FROM patch
	WHERE short_id = :short_id;


-- name: do-duplicate-patch<!
INSERT INTO patch (short_id, patch_data, based_on_id)
    SELECT :new_short_id, patch_data, id
	    FROM patch
		WHERE short_id = :old_short_id;


-- name: do-mark-patch-read-only!
UPDATE patch SET read_only = TRUE WHERE short_id = :short_id;
