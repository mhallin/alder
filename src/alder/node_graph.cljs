(ns alder.node-graph
  (:require [alder.node :as node]
            [alder.node-type :as node-type]
            [alder.builtin-nodes :as builtin-nodes]
            [alder.geometry :as geometry]
            [taoensso.timbre :refer-macros [debug]]
            [schema.core :as s :include-macros true]))

(defonce id-counter (atom 0))

(def NodeRef [(s/one s/Keyword "node-id") (s/one node/NodeSchema "node")])

(def SlotRef [(s/one s/Keyword "node-id") (s/one s/Keyword "slot-id")])

(def Connection [(s/one SlotRef "from") (s/one SlotRef "to")])

(def NodeGraph
  {:nodes {s/Keyword node/NodeSchema}
   :connections [Connection]
   :name s/Str})

(s/defn next-node-id :- s/Keyword
  [node-graph :- NodeGraph]
  (let [next-id (swap! id-counter inc)
        node-id (keyword (str "node-" next-id))]
    (if (node-id (:nodes node-graph))
      (next-node-id node-graph)
      node-id)))

(s/defn make-node-graph :- NodeGraph
  []
  {:nodes {}
   :connections []
   :name "Untitled patch"})

(s/defn add-node :- NodeGraph
  ([node-graph :- NodeGraph node-id :- s/Keyword node :- node/NodeSchema]
   (assoc-in node-graph [:nodes node-id] node))

  ([node-graph :- NodeGraph
    node-id :- s/Keyword
    node-type-id :- s/Keyword
    position :- geometry/Point
    context :- s/Any]
   (let [node (builtin-nodes/make-node context position node-type-id)
         node-graph (assoc-in node-graph [:nodes node-id] node)]
     node-graph)))

(s/defn set-connection [node-graph :- NodeGraph
                        [from-id output-id] :- SlotRef
                        [to-id input-id] :- SlotRef
                        f :- s/Any]
  (let [from-node (-> node-graph :nodes from-id)
        from-audio-node (node/audio-node from-node)

        to-node (-> node-graph :nodes to-id)
        to-audio-node (node/audio-node to-node)

        output-data (-> from-node node/node-type :outputs output-id)
        output-index (:index output-data)

        input-data (-> to-node node/node-type :inputs input-id)
        input-name (:name input-data)
        input-index (:index input-data)]
    (when-not (= (:type output-data) :null-node)
      (debug "Connection indices" output-index input-index)
      (case (:type input-data)
        :param (f from-audio-node [(aget to-audio-node input-name) output-index])
        :node (f from-audio-node [to-audio-node output-index input-index])
        :gate (f from-audio-node [to-audio-node output-index input-name])
        :constant (f from-audio-node [to-audio-node output-index input-name])
        :accessor (f from-audio-node [to-audio-node output-index input-name])
        :null-node nil))))

(s/defn connect-nodes :- NodeGraph
  [node-graph :- NodeGraph from :- SlotRef to :- SlotRef]
  (let [[from-node-id from-slot-id] from
        [to-node-id to-slot-id] to
        from-node (-> node-graph :nodes from-node-id)
        to-node (-> node-graph :nodes to-node-id)]
    (if (node/can-connect [from-node from-slot-id] [to-node to-slot-id])
      (do
        (debug "connect nodes" from to)
        (set-connection node-graph from to #(.apply (aget %1 "connect") %1 (clj->js %2)))
        (update-in node-graph [:connections]
                   (fn [conns] (conj conns [from to]))))
      node-graph)))

(s/defn disconnect-nodes :- NodeGraph
  [node-graph :- NodeGraph from :- SlotRef to :- SlotRef]
  (debug "disconnect nodes" from to)
  (set-connection node-graph from to #(.apply (aget %1 "disconnect") %1 (clj->js %2)))
  (update-in node-graph [:connections]
             (fn [conns] (vec (remove #(= % [from to]) conns)))))

(s/defn node-move-to :- NodeGraph
  [node-graph :- NodeGraph node-id :- s/Keyword position :- geometry/Point]
  (update-in node-graph [:nodes node-id]
             (fn [node] (node/node-move-to node position))))

(s/defn node-move-by :- NodeGraph
  [node-graph :- NodeGraph node-id :- s/Keyword offset :- geometry/Point]
  (update-in node-graph [:nodes node-id]
             (fn [node] (node/node-move-by node offset))))

(s/defn nodes-move-by :- NodeGraph
  [node-graph :- NodeGraph node-ids :- #{s/Keyword} offset :- geometry/Point]
  (reduce (fn [node-graph node-id]
            (node-move-by node-graph node-id offset))
          node-graph
          node-ids))

(s/defn node-position :- geometry/Point
  [node-graph :- NodeGraph node-id :- s/Keyword]
  (-> node-graph :nodes node-id node/frame geometry/rectangle-origin))

(s/defn hit-test-slot :- (s/maybe [(s/one s/Keyword "node-id")
                                   (s/one s/Keyword "slot-id")])
  [node-graph :- NodeGraph position :- geometry/Point]
  (when-let [matching (keep (fn [[id node]]
                              (when-let [slot-id (node/hit-test-slot node position)]
                                [id slot-id]))
                            (:nodes node-graph))]
    (first matching)))

(s/defn nodes-in-to :- [Connection]
  [node-graph :- NodeGraph node-id :- s/Keyword]
  (filter (fn [[_ [id _]]] (= id node-id))
          (:connections node-graph)))

(s/defn nodes-out-from :- [Connection]
  [node-graph :- NodeGraph node-id :- s/Keyword]
  (filter (fn [[[id _] _]] (= id node-id))
          (:connections node-graph)))

(s/defn remove-node :- NodeGraph
  [node-graph :- NodeGraph node-id :- s/Keyword]
  (let [connections (filter (fn [[[from-id _] [to-id _]]]
                              (or (= from-id node-id) (= to-id node-id)))
                            (:connections node-graph))
        node-graph (reduce (fn [acc [from to]]
                             (disconnect-nodes acc from to))
                           node-graph
                           connections)]
    (update-in node-graph [:nodes]
               #(dissoc % node-id))))

(s/defn remove-nodes :- NodeGraph
  [node-graph :- NodeGraph node-ids :- #{s/Keyword}]
  (reduce remove-node node-graph node-ids))

(s/defn disconnected-inputs :- [node-type/InputRef]
  [node-graph :- NodeGraph node-id :- s/Keyword inputs :- [node-type/InputRef]]
  (remove (fn [[id _]] (some (fn [[[_ _] [to-node-id to-input-id]]]
                               (and (= to-node-id node-id) (= to-input-id id)))
                             (:connections node-graph)))
          inputs))

(s/defn node-by-id :- node/NodeSchema
  [node-graph :- NodeGraph node-id :- s/Keyword]
  (-> node-graph :nodes node-id))

(s/defn editable-inputs :- [node-type/InputRef]
  [node-graph :- NodeGraph node-id :- s/Keyword]
  (filter (fn [[id s]]
            (and
             (not= (:type s) :node)
             (not-any? (fn [[[from-node-id from-output-id] [to-node-id to-input-id]]]
                         (and (= to-node-id node-id)
                              (= to-input-id id)
                              (= (-> (node-by-id node-graph from-node-id)
                                     (node/node-output from-output-id)
                                     :data-type)
                                    :param)))
                       (:connections node-graph))))
          (-> node-graph :nodes node-id node/node-type :inputs)))

(s/defn nodes-in-rect :- [NodeRef]
  [node-graph :- NodeGraph rect :- geometry/Rectangle]
  (filter (fn [[id n]] (geometry/rectangles-overlap? rect (node/frame n)))
          (:nodes node-graph)))
