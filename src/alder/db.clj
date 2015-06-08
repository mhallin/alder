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

(extend-protocol jdbc/IResultSetReadColumn
  PGobject
  (result-set-read-column [pgobj metadata idx]
    (let [type (.getType pgobj)
          value (.getValue pgobj)]
      (case type
        "jsonb" (json/read-str value :key-fn keyword)
        :else value))))

(defn- str->jsonb [s]
  (doto (PGobject.)
    (.setType "jsonb")
    (.setValue s)))

(defn generate-short-id []
  (->> (range 12)
       (map (fn [_] (rand-nth "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01234567890")))
       (string/join)))

(defn migrate []
  (rcore/migrate-all (rcore/connection database-url) (rfiles/migrations)))

(defn create-patch! []
  (let [short-id (generate-short-id)]
    (do-create-patch<! database-url short-id (jdbc/sql-value {}))))

(defn save-patch! [short-id patch-data]
  (do-save-patch! database-url (str->jsonb patch-data) short-id))

(defn get-patch [short-id]
  (first (do-get-patch database-url short-id)))
