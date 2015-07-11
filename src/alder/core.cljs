(ns ^:figwheel-always alder.core
    (:require [om.core :as om :include-macros true]
              [cljs.core.async :refer [<! >! put! close! chan alts!]]
              [taoensso.timbre :as timbre :refer-macros [error info debug]]
              [schema.core :as s]

              [alder.app-state :as app-state]
              [alder.node-graph-serialize :as node-graph-serialize]
              [alder.routes :as routes]
              [alder.comm :as comm]
              [alder.persist :as persist]
              [alder.log-util :as log-util]
              [alder.ui.components.root :refer [root-component]])

    (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]]))

(enable-console-print!)

(defn- dispatch-route [app page page-args]
  (debug "Dispatching route" page page-args)
  (if (= page :show-patch)
    (go
      (let [{:keys [short-id]} page-args
            chan (comm/get-serialized-graph short-id)
            response (<! chan)]
        (debug "Got response from get-patch" response)
        (when (= :patch-data (first response))
          (let [[_ serialized-graph] response
                graph-js-obj (.parse js/JSON serialized-graph)
                node-graph (node-graph-serialize/materialize-graph (:context @app)
                                                                   graph-js-obj)]
            (swap! app #(merge % {:current-page page
                                  :current-page-args page-args
                                  :node-graph node-graph}))))

        (when (= :create-new (first response))
          (let [[_ {:keys [short-id]}] response]
            (routes/replace-navigation! (routes/show-patch {:short-id short-id}))))))
    (swap! app #(merge % {:current-page page
                          :current-page-args page-args}))))

(timbre/merge-config! {:appenders {:console (log-util/granular-console-appender)}})

(routes/set-routing-callback! (partial dispatch-route app-state/app-state))
(routes/dispatch!)
(comm/start-socket-connection)
(persist/watch-state app-state/app-state)

(om/root root-component
         app-state/app-state
         {:target (. js/document (getElementById "app"))})

;; This ensures that Schema is always validating function
;; calls/responses when assertions are active
(assert
 (do (s/set-fn-validation! true) true))

(comment

  ;; Eval this in a REPL to disable logging for specific modules, or
  ;; change the logging configuration in any other way.
  (timbre/merge-config! {:ns-blacklist ["alder.persist"]})

  )

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
