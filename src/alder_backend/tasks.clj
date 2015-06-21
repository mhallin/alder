(ns alder-backend.tasks
  (:require [taoensso.timbre :refer [debug]]
            [taoensso.carmine :as car :refer [wcar]]
            [taoensso.carmine.message-queue :as car-mq]

            [environ.core :refer [env]]

            [alder-backend.db :as db])

  (:import [java.io ByteArrayInputStream]))

(defmulti process-task :command)

(defmethod process-task :garbage-collect [cmd]
  (db/garbage-collect-patches!)
  {:status :success})


(defn start-worker []
  (car-mq/worker (env :redis-spec) "alder_tasks"
                 {:handler (fn [{:keys [message]}]
                             (process-task message))}))
