(ns alder.node-graph-serialize
  (:require [clojure.walk :as walk]

            [alder.node :as node]
            [alder.node-graph :as node-graph]
            [alder.geometry :as geometry]))

(defn- serialize-input [node [input-id input]]
  [input-id (node/current-input-value node input)])

(defn- input-needs-serialization? [node-graph node-id [input-id input]]
  (and (not= (:type input) :node)
       (if (nil? (:serializable input)) true (:serializable input))
       (not-any? (fn [[[_ _] [_ id]]] (= input-id id))
                 (node-graph/nodes-in-to node-graph node-id))))

(defn- serialize-node [node-graph [node-id node]]
  [node-id {:node-type-id (node/node-type-id node)
            :frame (geometry/rectangle->vec (node/frame node))
            :inputs (into {} (map (partial serialize-input node)
                                  (filter (partial input-needs-serialization?
                                                   node-graph
                                                   node-id)
                                          (:inputs (node/node-type node)))))
            :inspector-visible (node/inspector-visible node)}])

(defn serialize-graph [node-graph]
  (let [data {:connections (:connections node-graph)
              :nodes (into {} (map (partial serialize-node node-graph)
                                   (:nodes node-graph)))
              :name (:name node-graph)}]
    (clj->js data)))


(defn- materialize-node [context node-graph [node-id {:keys [frame
                                                             inputs
                                                             node-type-id
                                                             inspector-visible]}]]
  (let [[x y _ _] frame
        node-graph (node-graph/add-node node-graph
                                        node-id
                                        (keyword node-type-id)
                                        [x y]
                                        context)
        node-graph (update-in node-graph [:nodes node-id]
                              #(node/set-inspector-visible % inspector-visible))
        node (node-id (:nodes node-graph))
        node-type (node/node-type node)]
    (doseq [[input-id value] inputs]
      (let [input (input-id (:inputs node-type))]
        (node/set-input-value node input value)))
    node-graph))

(defn- materialize-connection [node-graph [[from-node from-slot] [to-node to-slot]]]
  (node-graph/connect-nodes node-graph
                            [(keyword from-node) (keyword from-slot)]
                            [(keyword to-node) (keyword to-slot)]))

(defn materialize-graph [context serialized-graph]
  (let [{:keys [nodes connections name]} (walk/keywordize-keys
                                          (js->clj serialized-graph))
        node-graph (node-graph/make-node-graph)
        node-graph (assoc node-graph :name (or name "Untitled patch"))
        node-graph (reduce (fn [graph n] (materialize-node context graph n))
                           node-graph
                           nodes)
        node-graph (reduce materialize-connection
                           node-graph
                           connections)]
    node-graph))
