(ns alder.node-graph-serialize
  (:require [alder.node :as node]
            [alder.node-graph :as node-graph]
            [alder.geometry :as geometry]))

(defn- serialize-input [node [input-id input]]
  [input-id (node/current-input-value node input)])

(defn- input-needs-serialization? [node-graph node-id [input-id input]]
  (and (not= (:type input) :node)
       (not-any? (fn [[[_ _] [_ id]]] (= input-id id))
                 (node-graph/nodes-in-to node-graph node-id))))

(defn- serialize-node [node-graph [node-id node]]
  [node-id {:node-type-id (:node-type-id node)
            :frame (geometry/rectangle->vec (:frame node))
            :inputs (into {} (map (partial serialize-input node)
                                  (filter (partial input-needs-serialization?
                                                   node-graph
                                                   node-id)
                                          (:inputs (node/node-type node)))))}])

(defn serialize-graph [node-graph]
  (let [data {:connections (:connections node-graph)
              :nodes (into {} (map (partial serialize-node node-graph)
                                   (:nodes node-graph)))}]
    (clj->js data)))
