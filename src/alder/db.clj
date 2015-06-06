(ns alder.db
  (:require [ragtime.core :as rcore]
            [ragtime.sql.files :as rfiles]

            [clojure.java.jdbc :as jdbc]
            [clojure.data.json :as json]
            
            [environ.core :refer [env]]
            [yesql.core :refer [defqueries]]
            [clojure.string :as string])

  (:import org.postgresql.util.PGobject))

(defqueries "alder/sql/patch.sql")

(def database-url (env :database-url))

(extend-protocol jdbc/ISQLValue
  clojure.lang.IPersistentMap
  (sql-value [value]
    (doto (PGobject.)
      (.setType "jsonb")
      (.setValue (json/write-str value)))))


(defn generate-short-id []
  (->> (range 12)
       (map (fn [_] (rand-nth "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01234567890")))
       (string/join)))

(defn migrate []
  (rcore/migrate-all (rcore/connection database-url) (rfiles/migrations)))

(defn create-patch! []
  (let [short-id (generate-short-id)]
    (save-patch<! database-url short-id (jdbc/sql-value {}))))
