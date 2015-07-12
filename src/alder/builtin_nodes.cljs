(ns alder.builtin-nodes
  (:require [schema.core :as s :include-macros true]
            [taoensso.timbre :refer-macros [debug]]

            [alder.geometry :as geometry]
            [alder.node :as node]
            [alder.node-type :as node-type]))

(defmethod node/frame :builtin [{:keys [frame]}]
  frame)

(defmethod node/set-frame :builtin [data frame]
  (assoc data :frame frame))


(defmethod node/inspector-visible :builtin [{:keys [inspector-visible]}]
  inspector-visible)

(defmethod node/set-inspector-visible :builtin [data inspector-visible]
  (assoc data :inspector-visible inspector-visible))


(defmethod node/stored-input-value :builtin [{:keys [input-values]} name]
  (input-values name))

(defmethod node/set-stored-input-value :builtin [data name value]
  (assoc-in data [:input-values name] value))


(defmethod node/node-type-id :builtin [{:keys [node-type-id]}]
  node-type-id)

(defmethod node/audio-node :builtin [{:keys [audio-node]}]
  audio-node)


(s/defn make-node
  [context :- s/Any position :- geometry/Point node-type-id :- s/Keyword]
  (let [node-type (node-type/get-node-type node-type-id)
        [width height] (:default-size node-type)
        [x y] position
        constructor (:constructor node-type)
        node {:tag :builtin
              :frame (geometry/Rectangle. x y width height)
              :audio-node (constructor context)
              :inspector-visible false
              :node-type-id node-type-id
              :input-values {}}
        node (node/assign-default-node-inputs node)]
    (when (.-start (node/audio-node node))
      (debug "Starting built-in audio node")
      (.start (node/audio-node node) 0))
    node))
