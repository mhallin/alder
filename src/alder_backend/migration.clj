(ns alder-backend.migration
  (:require [clojure.string :as string]
            [clojure.tools.namespace.find :as find]
            [clojure.java.io :as io]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as timbre :refer [debug]])
  (:import [java.util.jar JarFile])
  (:gen-class))

(defprotocol IMigration
  (up [this db])
  (down [this db]))

(def migration-dir "alder_backend/migrations")

(defn- wrap-sql-migration [migration-name direction migration-fn]
  (fn [db-spec]
    (debug "Executing migration" migration-name direction)
    (jdbc/with-db-connection [db db-spec]
      (jdbc/with-db-transaction [txn db]
        (migration-fn txn)))))

(defn- migration-name [var]
  (let [ns (:ns (meta var))
        ns-str (name (ns-name ns))
        ns-parts (string/split ns-str #"\.")]
    (last ns-parts)))

(defn- to-ragtime-migration [var]
  (let [m (var-get var)
        name (migration-name var)]
    {:id name
     :up (wrap-sql-migration name :up #(up m %))
     :down (wrap-sql-migration name :down #(down m %))}))

(defn- running-in-jar? []
  (.startsWith (str (io/resource migration-dir)) "jar:"))

(defn- migrations-in-jar []
  (let [this-jar (-> *ns*
                     class
                     .getProtectionDomain
                     .getCodeSource
                     .getLocation
                     .getPath
                     JarFile.)
        class-files (filter #(and (.startsWith % "alder_backend/migrations/")
                                  (.endsWith % "__init.class"))
                            (sort (map #(.getName %) (enumeration-seq (.entries this-jar)))))]
    (doseq [class-file class-files]
      (load (str "/" (string/replace class-file #"__init\.class$" ""))))
    (let [vars (map #(-> %
                         (string/replace #"__init\.class$" "")
                         (string/replace #"_" "-")
                         (string/replace #"/" ".")
                         symbol
                         find-ns
                         (ns-resolve 'migrations))
                    class-files)]
      (vec vars))))

(defn- migrations-as-files []
  (->> migration-dir io/resource io/file
       find/find-clojure-sources-in-dir
       sort
       (map #(-> % io/reader load-reader))))

(defn migrations []
  (let [migration-files (if (running-in-jar?)
                          (migrations-in-jar)
                          (migrations-as-files))
        migrations (map to-ragtime-migration migration-files)
        ]
    migrations))
