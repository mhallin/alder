(ns alder-backend.db
  (:require [alder-backend.migration :as migration]

            [ragtime.core :as rcore]
            [ragtime.sql.files :as rfiles]

            [clojure.java.jdbc :as jdbc]
            [clojure.data.json :as json]
            
            [environ.core :refer [env]]
            [yesql.core :refer [defqueries]]
            [clojure.string :as string]
            [taoensso.timbre :as timbre :refer [debug]])
  
  (:import org.postgresql.util.PGobject))

(defqueries "alder_backend/sql/patch.sql")

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
  (let [migrations (migration/migrations)]
    (debug "Found migrations" (mapv :id migrations))
    (rcore/migrate-all (rcore/connection database-url) migrations)))

(defn create-patch! []
  (let [short-id (generate-short-id)]
    (do-create-patch<! database-url short-id (jdbc/sql-value {}))))

(defn save-patch! [short-id patch-data]
  (do-save-patch! database-url (str->jsonb patch-data) short-id))

(defn get-patch [short-id]
  (first (do-get-patch database-url short-id)))
