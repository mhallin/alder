(ns alder.persist
  (:require [cljs.core.async :refer [<! >! put! close!]]
            [taoensso.timbre :refer-macros [info debug]]

            [alder.node-graph-serialize :as node-graph-serialize]
            [alder.comm :as comm])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce ignore-state-changes (atom false))
(defonce last-persisted-graph (atom nil))
(defonce last-persisted-patch-id (atom nil))

(defn- on-state-change [_ _ _ new-state]
  (let [node-graph (:node-graph new-state)
        patch-id (:short-id (:current-page-args new-state))]
    (when (and (not @ignore-state-changes)
               (not= @last-persisted-graph node-graph))
      (info "Saving" patch-id)
      (let [chan (->> node-graph
                      (node-graph-serialize/serialize-graph)
                      (.stringify js/JSON)
                      (comm/send-serialized-graph patch-id))]
        (go (let [resp (<! chan)]
              (debug "Serialize response" resp)))
        (reset! last-persisted-graph node-graph)))))

(defn watch-state [state]
  (remove-watch state :node-graph-serialize)
  (add-watch state :node-graph-serialize on-state-change))

(defn set-ignore-state-changes! [ignore]
  (reset! ignore-state-changes ignore))
