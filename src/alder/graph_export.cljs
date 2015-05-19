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
  (string/join "\n" (map write-create-node (:nodes node-graph))))

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
                 ");"))))

(defn- write-connections [node-graph]
  (string/join "\n" (map (partial write-connection node-graph)
                         (:connections node-graph))))

(defn javascript-node-graph [node-graph]
  (let [dependencies (write-dependencies node-graph)
        create-nodes (write-create-nodes node-graph)
        connections (write-connections node-graph)]
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
         "\n"
         "module.exports = GroupedNode;\n")))
