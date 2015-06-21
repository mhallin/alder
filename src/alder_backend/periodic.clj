(ns alder-backend.periodic
  (:require [clojure.core.async :as a :refer [<! >!! alts! go-loop]]

            [chime :refer [chime-ch]]
            [clj-time.core :as t]
            [clj-time.periodic :refer [periodic-seq]]

            [environ.core :refer [env]]

            [taoensso.carmine :as car :refer [wcar]]
            [taoensso.carmine.message-queue :as car-mq]
            [taoensso.timbre :refer [debug]])

  (:import [java.io ByteArrayOutputStream]))

(defonce current-cancel-chan (atom nil))

(defn- run-garbage-collector []
  (wcar (env :redis-spec)
        (car-mq/enqueue "alder_tasks" {:command :garbage-collect})))


(defn start-periodic-tasks []
  (when @current-cancel-chan
    (>!! @current-cancel-chan :terminate))

  (let [cancel-chan (a/chan)
        garbage-collect-chan (chime-ch (periodic-seq (t/now) (-> 5 t/secs))
                                       {:ch (a/chan (a/sliding-buffer 1))})]
    (reset! current-cancel-chan cancel-chan)
    (go-loop []
      (let [[v ch] (alts! [cancel-chan garbage-collect-chan])]
        (condp = ch
          cancel-chan
          (do
            (swap! current-cancel-chan #(if (= %1 cancel-chan) nil %1)))

          garbage-collect-chan
          (do
            (run-garbage-collector)
            (recur)))))
    cancel-chan))
