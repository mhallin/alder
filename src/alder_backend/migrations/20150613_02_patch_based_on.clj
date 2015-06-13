(ns alder-backend.migrations.20150613-02-patch-based-on
  (:require [alder-backend.migration :refer [IMigration]]
            [clojure.java.jdbc :as jdbc])
  (:gen-class))

(def migrations
  (reify
    IMigration
    (up [_ db] (jdbc/execute! db ["
ALTER TABLE patch
  ADD COLUMN based_on_id INTEGER REFERENCES patch(id) DEFAULT NULL
"]))

    (down [_ db] (jdbc/execute! db ["
ALTER TABLE patch
  DROP COLUMN based_on_id
"]))))
