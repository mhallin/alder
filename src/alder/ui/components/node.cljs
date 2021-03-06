(ns alder.ui.components.node
  (:require [om.core :as om]
            [sablono.core :as html :refer-macros [html]]
            [schema.core :as s :include-macros true]

            [alder.node :as node]
            [alder.geometry :as geometry]))

(defn- render-slot [node-id node slot-id slot slot-frame slot-drag-data on-mouse-down]
  (html
   [:div.node__slot
    {:style (geometry/rectangle->css slot-frame)
     :key (str slot-id)
     :class (if slot-drag-data
              [(if (node/can-connect slot-drag-data [node slot-id])
                 "m-connectable"
                 "m-not-connectable")]
              [])
     :title (:title slot)
     :on-mouse-down #(on-mouse-down node-id slot-id %)}]))

(defn- render-slot-list [node-id node slot-drag-data on-mouse-down]
  (let [slot-frames (node/slot-frames node)]
    (map (fn [[slot-id [slot slot-frame]]]
           (render-slot node-id node slot-id slot slot-frame slot-drag-data on-mouse-down))
         slot-frames)))

(defn node-component [[node-id node slot-drag-data selection graph-xform] owner
                      {:keys [on-mouse-down on-slot-mouse-down]}]
  (reify
    om/IDisplayName
    (display-name [_] "Node")

    om/IRender
    (render [_]
      (let [frame (node/frame node)
            title (-> node node/title)
            inspector-visible (node/inspector-visible node)]
        (html [:div.node
               {:style (geometry/rectangle->css
                        (geometry/rectangle-transform frame
                                                      (:matrix graph-xform)))
                :key (str "node__" node-id)
                :on-mouse-down #(on-mouse-down node-id %)
                :class (if (selection node-id) ["m-selected"] [])}
               title
               [:div.node__inspector-toggle-button
                {:class (if inspector-visible "m-open" "m-closed")
                 :on-click #(om/transact! node
                                          (fn [node]
                                            (node/set-inspector-visible
                                             node (not inspector-visible))))}]
               (render-slot-list node-id node slot-drag-data on-slot-mouse-down)])))))
