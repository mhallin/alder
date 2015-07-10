(ns alder.ui.interaction
  (:require [cljs.core.async :refer [<! >! chan]]

            [alder.geometry :as geometry]
            [alder.math :as math]
            [alder.node :as node]
            [alder.node-graph :as node-graph]
            [alder.persist :as persist])

  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(def palette-width 260)


(defmulti handle-dragging-sequence (fn [_ _ msg] (:type msg)))

(defmethod handle-dragging-sequence :selection [app read-chan {:keys [mouse-pos reply-chan]}]
  (let [done (chan)
        start-mouse-pos mouse-pos]
    (go
      (>! reply-chan [:set-rect (geometry/corners->rectangle mouse-pos mouse-pos)])
      (>! reply-chan [:update #{}])

      (loop []
        (let [{:keys [phase mouse-pos]} (<! read-chan)
              rect (geometry/rectangle-transform
                    (geometry/corners->rectangle start-mouse-pos mouse-pos)
                    (-> app :graph-xform :inv))
              nodes (set (map first (node-graph/nodes-in-rect (:node-graph app) rect)))]
          (>! reply-chan [:update nodes])

          (when (= phase :drag)
            (>! reply-chan [:set-rect rect])
            (recur))))

      (>! reply-chan [:clear-rect nil])
      (>! done :done))

    done))

(defmethod handle-dragging-sequence :node [app read-chan
                                           {:keys [ref-node-id offset reply-chan]}]
  (let [done (chan)]
    (go
      (persist/set-ignore-state-changes! true)
      (loop [last-pos (node-graph/node-position (:node-graph app) ref-node-id)]
        (let [{:keys [phase mouse-pos]} (<! read-chan)
              mouse-pos (math/mult-point (-> app :graph-xform :inv) mouse-pos)
              new-node-pos (geometry/point-sub mouse-pos offset)
              delta (geometry/point-sub new-node-pos last-pos)]
          (>! reply-chan [:offset delta])

          (when (= phase :drag)
            (recur new-node-pos))))

      (persist/set-ignore-state-changes! false)
      (>! done :done))
    done))

(defmethod handle-dragging-sequence :prototype-node [app read-chan
                                                     {:keys [node offset reply-chan]}]
  (let [done (chan)]
    (go
      (>! reply-chan [:update node])

      (loop [node node]
        (let [{:keys [phase mouse-pos]} (<! read-chan)
              abs-mouse-pos mouse-pos
              mouse-pos (math/mult-point (-> app :graph-xform :inv) mouse-pos)
              last-pos (geometry/rectangle-origin (:frame node))
              new-node-pos (geometry/point-sub mouse-pos offset)
              delta (geometry/point-sub new-node-pos last-pos)
              node (node/node-move-by node delta)]
          (>! reply-chan [:update node])

          (when (= phase :drag)
            (recur node))

          (when (and (= phase :end)
                     (< (first abs-mouse-pos)
                        (- (.-innerWidth js/window) palette-width)))
            (>! reply-chan [:commit node]))))

      (>! reply-chan [:clear nil])
      (>! done :done))
    done))

(defmethod handle-dragging-sequence :slot-drag [app read-chan
                                                {:keys [slot-path
                                                        disconnect-slot-path
                                                        reverse reply-chan
                                                        position]}]
  (let [done (chan)
        position (math/mult-point (-> app :graph-xform :inv) position)]
    (go
      (when disconnect-slot-path
        (>! reply-chan [:disconnect [slot-path disconnect-slot-path]]))
      (>! reply-chan [:update {:slot-path slot-path
                               :target-pos position}])

      (loop []
        (let [{:keys [phase mouse-pos]} (<! read-chan)
              mouse-pos (math/mult-point (-> app :graph-xform :inv) mouse-pos)
              ]
          (>! reply-chan [:update {:slot-path slot-path
                                   :target-pos mouse-pos}])
          (when (= phase :drag)
            (recur))

          (when (= phase :end)
            (when-let [hit (-> app :node-graph
                               (node-graph/hit-test-slot mouse-pos))]
              (let [from (if reverse hit slot-path)
                    to (if reverse slot-path hit)]
                (>! reply-chan [:connect [from to]]))))))

      (>! reply-chan [:clear nil])
      (>! done :done))
    done))
