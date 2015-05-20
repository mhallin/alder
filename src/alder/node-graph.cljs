(ns alder.node-graph
  (:require [alder.node :as node]
            [alder.geometry :as geometry]))

(defonce id-counter (atom 0))

(defn next-node-id []
  (let [next-id (swap! id-counter inc)]
    (keyword (str "node-" next-id))))


(defn make-node-graph []
  {:nodes {}
   :connections []})

(defn add-node [node-graph node-id node-type-id position context]
  (let [node (node/make-node context position node-type-id)
        node-graph (assoc-in node-graph [:nodes node-id] node)]
    node-graph))

(defn set-connection [node-graph [from-id output-id] [to-id input-id] f]
  (let [from-node (-> node-graph :nodes from-id)
        from-audio-node (:audio-node from-node)

        to-node (-> node-graph :nodes to-id)
        to-audio-node (:audio-node to-node)

        output-data (-> from-node node/node-type :outputs output-id)
        output-index (:index output-data)

        input-data (-> to-node node/node-type :inputs input-id)
        input-name (:name input-data)]
    (when-not (= (:type output-data) :null-node)
      (case (:type input-data)
        :param (f from-audio-node (aget to-audio-node input-name) output-index)
        :node (f from-audio-node to-audio-node output-index)
        :gate (f from-audio-node to-audio-node output-index)
        :null-node nil))))

(defn connect-nodes [node-graph from to]
  (println "connect" from to)
  (set-connection node-graph from to #(.call (aget %1 "connect") %1 %2 %3))
  (update-in node-graph [:connections]
               (fn [conns] (conj conns [from to]))))

(defn disconnect-nodes [node-graph from to]
  (println "disconnect" from to)
  (set-connection node-graph from to #(.call (aget %1 "disconnect") %1 %2 %3))
  (update-in node-graph [:connections]
             (fn [conns] (vec (remove #(= % [from to]) conns)))))

(defn node-move-to [node-graph node-id position]
  (update-in node-graph [:nodes node-id]
             (fn [node] (node/node-move-to node position))))


(defn hit-test-slot [node-graph position]
  (when-let [matching (keep (fn [[id node]]
                              (when-let [slot-id (node/hit-test-slot node position)]
                                [id slot-id]))
                            (:nodes node-graph))]
    (first matching)))

(defn nodes-in-to [node-graph node-id]
  (filter (fn [[_ [id _]]] (= id node-id))
          (:connections node-graph)))

(defn nodes-out-from [node-graph node-id]
  (filter (fn [[[id _] _]] (= id node-id))
          (:connections node-graph)))

(defn remove-node [node-graph node-id]
  (let [connections (filter (fn [[[from-id _] [to-id _]]]
                              (or (= from-id node-id) (= to-id node-id)))
                            (:connections node-graph))
        node-graph (reduce (fn [acc [from to]]
                             (disconnect-nodes acc from to))
                           node-graph
                           connections)]
    (update-in node-graph [:nodes]
               #(dissoc % node-id))))
