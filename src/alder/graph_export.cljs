(ns alder.graph-export
  (:require [clojure.string :as string]

            [alder.node :as node]
            [alder.node-graph :as node-graph]))

(defn- node-id->var [node-id]
  (string/replace (str node-id) #"[:-]" ""))

(defn- write-dependency [[class-name [file _]]]
  (str "var " class-name " = require('" file "');"))

(defn- write-dependencies [node-graph]
  (let [all-dependencies (apply merge
                          (keep (fn [[_ n]] (-> n node/node-type :export-data :dependencies))
                                (:nodes node-graph)))]
    (string/join "\n"
                 (keep write-dependency
                       (set all-dependencies)))))

(defn- write-set-input [node-id [_ input]]
  (case (:type input)
    :param (str "  " (node-id->var node-id)
                "." (:name input)
                ".value = " (:default input) ";")
    :constant (str "  " (node-id->var node-id)
                   "." (:name input)
                   " = " (pr-str (:default input)) ";")
    nil))

(defn- write-create-node [[node-id node]]
  (let [construct (str "  var " (node-id->var node-id)
                       " = "
                       (-> node node/node-type :export-data :constructor) ";")
        inputs (-> node node/node-type :inputs)
        props (string/join "\n"
                           (keep (partial write-set-input node-id)
                                 inputs))]
    (str construct "\n" props (if (string/blank? props) "" "\n"))))

(defn- write-create-nodes [node-graph]
  (->> node-graph
       :nodes
       (filter (fn [[_ n]] (-> n node/node-type :export-data :constructor)))
       (map write-create-node)
       (string/join "\n")))

(defn- write-connection [node-graph [[from-node-id from-slot-id] [to-node-id to-slot-id]]]
  (let [from-node-var (node-id->var from-node-id)
        to-node-var (node-id->var to-node-id)

        from-slot (-> node-graph :nodes from-node-id node/node-type :outputs from-slot-id)
        to-slot (-> node-graph :nodes to-node-id node/node-type :inputs to-slot-id)]

    (case (:type to-slot)
      :param (str "  " from-node-var
                  ".connect(" to-node-var "." (:name to-slot)
                  ", " (:index from-slot)
                  ");")
      :node (str "  " from-node-var
                 ".connect(" to-node-var
                 ", " (:index from-slot)
                 ");")
      :gate (str "  " from-node-var
                 ".connect(" to-node-var
                 ", " (:index from-slot)
                 ");")
      :null-node "")))

(defn- write-connections [node-graph]
  (->> node-graph
       :connections
       (map (partial write-connection node-graph))
       (remove string/blank?)
       (string/join "\n")))

(defn- graph-without-internal-nodes [node-graph]
  (let [nodes (into {}
                    (remove (fn [[_ n]] (-> n node/node-type :export-data :ignore-export))
                            (:nodes node-graph)))
        node-ids (set (keys nodes))
        connections (filter (fn [[[id1 _] [id2 _]]]
                              (and (node-ids id1) (node-ids id2)))
                            (:connections node-graph))]
    (-> node-graph
        (assoc :nodes nodes)
        (assoc :connections connections))))

(defn- write-connect-destinations [node-graph to-node-id]
  (->> to-node-id
       (node-graph/nodes-in-to node-graph)
       (map (fn [[[from-id _] _]]
              (str "    this." (node-id->var from-id)
                   ".connect(destination, input);")))
       (string/join "\n")))

(defn- write-disconnect-destinations [node-graph to-node-id]
  (->> to-node-id
       (node-graph/nodes-in-to node-graph)
       (map (fn [[[from-id _] _]]
              (str "    this." (node-id->var from-id)
                   ".disconnect();")))
       (string/join "\n")))

(defn- write-output-if-statements [node-graph f]
  (->> node-graph
       :nodes
       (filter (fn [[_ n]] (-> n node/node-type :export-data :type (= :output))))
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
       "};\n"))

(defn- write-disconnect-function [node-graph]
  (str "GraupedNode.prototype.disconnect = function (output) {\n"
       "  var output = output || 0;\n"
       "\n"
       (write-output-if-statements node-graph write-disconnect-destinations)
       "};\n"))

(defn javascript-node-graph [node-graph]
  (let [node-graph (graph-without-internal-nodes node-graph)
        dependencies (write-dependencies node-graph)
        create-nodes (write-create-nodes node-graph)
        connections (write-connections node-graph)
        connect-function (write-connect-function node-graph)
        disconnect-function (write-disconnect-function node-graph)]
    (str "'use strict';\n"
         "\n"
         dependencies "\n"
         "\n"
         "function GroupedNode(context) {\n"
         create-nodes "\n"
         "\n"
         connections "\n"
         "}\n"
         "\n"
         connect-function
         "\n"
         disconnect-function
         "\n"
         "\n"
         "module.exports = GroupedNode;\n")))
