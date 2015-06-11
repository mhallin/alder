(ns alder.views.node
  (:require [om.core :as om]
            [sablono.core :as html :refer-macros [html]]

            [alder.node :as node]
            [alder.geometry :as geometry]))

(defn- render-slot [node-id node slot-id slot slot-frame slot-drag-data on-mouse-down]
  (html
   [:div.graph-canvas__node-slot
    {:style (geometry/rectangle->css slot-frame)
     :class (if slot-drag-data
              [(if (node/can-connect slot-drag-data [node slot-id])
                 "m-connectable"
                 "m-not-connectable")]
              [])
     :title (:title slot)
     :on-mouse-down #(on-mouse-down node-id slot-id %)}]))

(defn- render-slot-list [node-id node slot-drag-data on-mouse-down]
  (let [slot-frames (node/node-slot-frames node)]
    (map (fn [[slot-id [slot slot-frame]]]
           (render-slot node-id node slot-id slot slot-frame slot-drag-data on-mouse-down))
         slot-frames)))

(defn node-component [[node-id node slot-drag-data selection] owner
                      {:keys [on-mouse-down on-slot-mouse-down]}]
  (reify
    om/IDisplayName
    (display-name [_] "Node")

    om/IRender
    (render [_]
      (let [frame (:frame node)
            title (-> node node/node-type :default-title)]
        (html [:div.graph-canvas__node
               {:style (geometry/rectangle->css frame)
                :key (str "node__" node-id)
                :on-mouse-down #(on-mouse-down node-id %)
                :class (if (selection node-id) ["m-selected"] [])}
               title
               [:div.graph-canvas__node-inspector-toggle
                {:class (if (:inspector-visible node) "m-open" "m-closed")
                 :on-click #(om/transact! node :inspector-visible (fn [x] (not x)))}]
               (render-slot-list node-id node slot-drag-data on-slot-mouse-down)])))))
