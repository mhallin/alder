(ns alder.dragging
  (:require [om.core :as om :include-macros true]
            [schema.core :as s :include-macros true]
            [cljs.core.async :refer [>!]]

            [alder.geometry :as geometry]
            [alder.node :as node])

  (:require-macros [cljs.core.async.macros :refer [go]]))

(s/defn start-selection-drag [app mouse-pos :- geometry/Point reply-chan]
  (go
    (>! (:dragging-chan @app) {:phase :start,
                               :type :selection,
                               :mouse-pos mouse-pos,
                               :reply-chan reply-chan})))

(s/defn start-node-drag [app ref-node-id :- s/Keyword offset :- geometry/Point reply-chan]
  (go
    (>! (:dragging-chan @app) {:phase :start,
                               :type :node,
                               :ref-node-id ref-node-id,
                               :offset offset,
                               :reply-chan reply-chan})))

(s/defn start-prototype-node-drag [app
                                   node :- node/NodeSchema
                                   offset :- geometry/Point
                                   reply-chan]
  (go
    (>! (:dragging-chan @app) {:phase :start,
                               :type :prototype-node,
                               :node node,
                               :offset offset,
                               :reply-chan reply-chan})))

(s/defn start-slot-drag [app
                         node-id :- s/Keyword
                         slot-id :- s/Keyword
                         position :- geometry/Point
                         reverse :- s/Bool
                         reply-chan]
  (go
    (>! (:dragging-chan @app) {:phase :start
                               :type :slot-drag
                               :slot-path [node-id slot-id]
                               :position position
                               :reverse reverse
                               :reply-chan reply-chan})))

(s/defn mouse-drag [app mouse-pos :- geometry/Point]
  (go
    (>! (:dragging-chan @app) {:phase :drag, :mouse-pos mouse-pos})))

(s/defn mouse-up [app mouse-pos :- geometry/Point]
  (go
    (>! (:dragging-chan @app) {:phase :end, :mouse-pos mouse-pos})))
