(ns alder-backend.migrations.20150613-01-readonly-patch
  (:require [alder-backend.migration :refer [IMigration]]
            [clojure.java.jdbc :as jdbc])
  (:gen-class))

(def migrations
  (reify
    IMigration
    (up [_ db] (jdbc/execute! db ["
ALTER TABLE patch
  ADD COLUMN read_only BOOLEAN NOT NULL DEFAULT FALSE
"]))

    (down [_ db] (jdbc/execute! db ["
ALTER TABLE patch
  DROP COLUMN read_only
"]))))
