(ns alder.views.connection
  (:require [cljs.core.async :refer [>!]]
            [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]

            [alder.node :as node]
            [alder.geometry :as geometry]
            [alder.ui.dragging :as dragging]
            [alder.dom-util :as dom-util])

  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- slot-disconnect-and-start-drag [app
                                       from-node-id from-slot-id
                                       to-node-id to-slot-id event]
  (.stopPropagation event)

  (let [from [from-node-id from-slot-id]
        to [to-node-id to-slot-id]
        mouse-pos (dom-util/event-mouse-pos event)]
    (dragging/disconnect-and-start-slot-drag app from to mouse-pos (:slot-drag-chan app))))

(defn- connection-line [[from-x from-y] [to-x to-y] on-mouse-down]
  (let [diff-x (- to-x from-x)
        diff-y (- to-y from-y)]
    (html
     [:path.graph-canvas__connection
      {:d (str "M " (+ 1 from-x) "," (+ 1 from-y) " "
               "c "
               (/ diff-x 2) ",0 "
               (/ diff-x 2) "," diff-y " "
               diff-x "," diff-y)
       :on-mouse-down #(on-mouse-down %)}])))


(defn connection-component [[app
                             [from-node-id from-node from-slot-id]
                             [to-node-id to-node to-slot-id]]
                            owner]
  (reify
    om/IDisplayName
    (display-name [_] "Connection")

    om/IRender
    (render [_]
      (let [[_ from-slot-frame] (-> from-node
                                    node/node-slot-canvas-frames
                                    from-slot-id)

            [_ to-slot-frame] (-> to-node
                                  node/node-slot-canvas-frames
                                  to-slot-id)]
        (connection-line (geometry/rectangle-center from-slot-frame)
                         (geometry/rectangle-center to-slot-frame)
                         #(slot-disconnect-and-start-drag app
                                                          from-node-id
                                                          from-slot-id
                                                          to-node-id
                                                          to-slot-id
                                                          %))))))

(defn temporary-connection-component [[from-coord to-coord] owner]
  (reify
    om/IDisplayName
    (display-name [_] "TemporaryConnection")

    om/IRender
    (render [_]
      (connection-line from-coord
                       to-coord
                       nil))))
