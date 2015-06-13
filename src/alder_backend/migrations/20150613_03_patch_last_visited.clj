(ns alder-backend.migrations.20150613-03-patch-last-visited
  (:require [alder-backend.migration :refer [IMigration]]
            [clojure.java.jdbc :as jdbc])
  (:gen-class))

(def migrations
  (reify
    IMigration
    (up [_ db]
      (jdbc/execute! db ["
ALTER TABLE patch
  ADD COLUMN last_visited_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
"])
      (jdbc/execute! db ["
UPDATE patch SET last_visited_at = created_at
"]))

    (down [_ db] (jdbc/execute! db ["
ALTER TABLE patch
  DROP COLUMN last_visited_at
"]))))
