(ns alder-backend.migrations.20150602-01-create-patch-table
  (:require [alder-backend.migration :refer [IMigration]]
            [clojure.java.jdbc :as jdbc])
  (:gen-class))

(def migrations
  (reify
    IMigration
    (up [_ db] (jdbc/execute! db ["
CREATE TABLE patch (
  id SERIAL NOT NULL PRIMARY KEY,
  short_id CHAR(12) NOT NULL UNIQUE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  patch_data JSONB NOT NULL
)"]))

    (down [_ db] (jdbc/execute! db ["
DROP TABLE patch
"]))))
