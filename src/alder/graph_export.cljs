(ns alder.graph-export
  (:require [clojure.string :as string]

            [alder.node :as node]
            [alder.node-graph :as node-graph]))

(defn- node-id->var [node-id]
  (string/replace (str node-id) #"[:-]" ""))

(defn- node-graph-node [node-graph node-id]
  (-> node-graph :nodes node-id))

(defn- node-dependencies [node]
  (-> node node/export-data :dependencies))

(defn- node-constructor [node]
  (-> node node/export-data :constructor))

(defn- node-export-data-type [node]
  (-> node node/export-data :type))

(defn- node-ignores-export? [node]
  (-> node node/export-data :ignore-export))


(defn- write-dependency [[class-name [file _]]]
  (str "var " class-name " = require('" file "');"))

(defn- write-dependencies [node-graph]
  (->> (:nodes node-graph)
       (keep (fn [[_ n]] (node-dependencies n)))
       (apply merge)
       (map write-dependency)
       (string/join "\n")))


(defmulti write-set-input (fn [_ [_ input]] (:type input)))

(defmethod write-set-input :param [node-id [_ input]]
  (str "  " (node-id->var node-id)
       "." (:name input)
       ".value = " (:default input) ";"))

(defmethod write-set-input :constant [node-id [_ input]]
  (str "  " (node-id->var node-id)
       "." (:name input)
       " = " (pr-str (:default input)) ";"))

(defmethod write-set-input :default [_ _] nil)



(defn- write-create-node [[node-id node]]
  (let [construct (str "  var " (node-id->var node-id)
                       " = " (node-constructor node))
        props (->> (node/inputs node)
                   (keep (partial write-set-input node-id))
                   (string/join "\n"))]
    (str construct "\n"
         props (if (string/blank? props) "" "\n"))))

(defn- write-create-nodes [node-graph]
  (->> (:nodes node-graph)
       (filter (fn [[_ n]] (node-constructor n)))
       (map write-create-node)
       (string/join "\n")))



(defmulti write-connect-call (fn [_ _ _ to-slot] (:type to-slot)))

(defmethod write-connect-call :param [from-node-var from-slot to-node-var to-slot]
  (str "  " from-node-var
       ".connect(" to-node-var "." (:name to-slot)
       ", " (:index from-slot)
       ");"))

(defmethod write-connect-call :null-node [_ _ _ _] nil)

(defmethod write-connect-call :default [from-node-var from-slot to-node-var to-slot]
  (str "  " from-node-var
       ".connect(" to-node-var
       ", " (:index from-slot)
       ");"))


(defn- write-connection [node-graph [[from-node-id from-slot-id] [to-node-id to-slot-id]]]
  (let [from-node-var (node-id->var from-node-id)
        to-node-var (node-id->var to-node-id)

        from-slot (node/output (node-graph-node node-graph from-node-id)
                               from-slot-id)
        to-slot (node/input (node-graph-node node-graph from-node-id)
                            to-slot-id)]

    (when-not (= (:type from-slot) :null-node)
      (write-connect-call from-node-var from-slot to-node-var to-slot))))

(defn- write-connections [node-graph]
  (->> (:connections node-graph)
       (map (partial write-connection node-graph))
       (remove nil?)
       (string/join "\n")))



(defn- write-property-input [node-graph [from-node-id _]]
  (->> (node-graph/nodes-out-from node-graph from-node-id)
       (map (fn [[_ [to-id to-slot-id]]]
              [to-id (node/input (node-graph-node node-graph to-id)
                                 to-slot-id)]))
       (filter (fn [[_ slot]] (#{:param :node} (:type slot))))
       (map (fn [[node-id slot]]
              (str "  this." (or (:name slot) (node-id->var node-id))
                   " = " (node-id->var node-id) (if (= (:type slot) :param)
                                                  (str "." (:name slot))
                                                  "")
                   ";")))
       (string/join "\n")))

(defn- write-property-inputs [node-graph]
  (->> (:nodes node-graph)
       (filter (fn [[_ n]] (-> n node-export-data-type (= :input))))
       (map (partial write-property-input node-graph))
       (remove string/blank?)
       (string/join "\n")))



(defn- graph-without-ignored-nodes [node-graph]
  (let [nodes (into {}
                    (remove (fn [[_ n]] (node-ignores-export? n))
                            (:nodes node-graph)))
        node-ids (set (keys nodes))
        connections (filter (fn [[[id1 _] [id2 _]]]
                              (and (node-ids id1) (node-ids id2)))
                            (:connections node-graph))]
    (-> node-graph
        (assoc :nodes nodes)
        (assoc :connections connections))))


(defn- write-connect-destinations [node-graph to-node-id]
  (->> (node-graph/nodes-in-to node-graph to-node-id)
       (map (fn [[[from-id _] _]]
              (str "    this." (node-id->var from-id)
                   ".connect(destination, input);")))
       (string/join "\n")))

(defn- write-disconnect-destinations [node-graph to-node-id]
  (->> (node-graph/nodes-in-to node-graph to-node-id)
       (map (fn [[[from-id _] _]]
              (str "    this." (node-id->var from-id)
                   ".disconnect();")))
       (string/join "\n")))

(defn- write-output-if-statements [node-graph f]
  (->> (:nodes node-graph)
       (filter (fn [[_ n]] (-> n node-export-data-type (= :output))))
       (map-indexed (fn [i [id _]]
                      (str "  if (output === " i ") {\n"
                           (f node-graph id) "\n"
                           "  }\n")))
       (string/join "\n")))

(defn- write-connect-function [node-graph]
  (str "GroupedNode.prototype.connect = function (destination, output, input) {\n"
       "  var output = output || 0;\n"
       "\n"
       (write-output-if-statements node-graph write-connect-destinations)
       "};"))

(defn- write-disconnect-function [node-graph]
  (str "GroupedNode.prototype.disconnect = function (output) {\n"
       "  var output = output || 0;\n"
       "\n"
       (write-output-if-statements node-graph write-disconnect-destinations)
       "};"))



(defn- write-constructor [node-graph]
  (let [create-nodes (write-create-nodes node-graph)
        connections (write-connections node-graph)
        param-inputs (write-property-inputs node-graph)]
    (str "function GroupedNode(context) {\n"
         (string/join "\n"
                      [create-nodes connections param-inputs])
         "\n"
         "}")))


(defn- write-property-accessor-function [to-id slot]
  (str "GroupedNode.prototype." (:name slot) " = function (value) {\n"
       "  return this." (node-id->var to-id)
       "." (:name slot) "(value);\n"
       "};"))


(defmulti write-accessor-function (fn [_ slot] (:type slot)))

(defmethod write-accessor-function :accessor [to-id slot]
  (write-property-accessor-function to-id slot))

(defmethod write-accessor-function :gate [to-id slot]
  (write-property-accessor-function to-id slot))

(defmethod write-accessor-function :constant [to-id slot]
  (str "GroupedNode.prototype." (:name slot) " = function (value) {\n"
       "  if (value === undefined) {\n"
       "    return this." (node-id->var to-id) "." (:name slot) ";\n"
       "  }\n"
       "  else {\n"
       "    this." (node-id->var to-id) "." (:name slot) " = value;\n"
       "  }\n"
       "};"))

(defmethod write-accessor-function :default [_ _] "")

(defn- write-property-input-function [node-graph [from-node-id node]]
  (->> from-node-id
       (node-graph/nodes-out-from node-graph)
       (map (fn [[_ [to-id to-slot-id]]]
              [to-id (-> node-graph :nodes to-id (node/input to-slot-id))]))
       (filter (fn [[node-id slot]] (#{:gate :accessor :constant} (:type slot))))
       (map (fn [[node-id slot]] (write-accessor-function node-id slot)))
       (string/join "\n")))

(defn- write-property-input-functions [node-graph]
  (->> node-graph
       :nodes
       (filter (fn [[_ n]](-> n node-export-data-type (= :input))))
       (map (partial write-property-input-function node-graph))
       (remove string/blank?)
       (string/join "\n")))



(defn javascript-node-graph [node-graph]
  (let [node-graph (graph-without-ignored-nodes node-graph)
        dependencies (write-dependencies node-graph)
        constructor (write-constructor node-graph)
        connect-function (write-connect-function node-graph)
        disconnect-function (write-disconnect-function node-graph)
        input-accessor-functions (write-property-input-functions node-graph)]
    (string/join "\n"
                 ["'use strict';" ""
                  dependencies ""
                  constructor ""
                  connect-function ""
                  disconnect-function ""
                  input-accessor-functions ""
                  "module.exports = GroupedNode;" ""])))
