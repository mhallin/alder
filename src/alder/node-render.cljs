(ns alder.node-render
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]

            [alder.node :as node]
            [alder.geometry :as geometry]))


(defn- render-slot-list [node-id node on-mouse-down]
  (let [slot-frames (node/node-slot-frames node)]
    (map (fn [[slot-id [slot slot-frame]]]
           [:div.graph-canvas__node-slot
            {:style (geometry/rectangle->css slot-frame)
             :title (:title slot)
             :on-mouse-down #(on-mouse-down node-id slot-id %)}])
         slot-frames)))


(defn node-component [[node-id node] owner {:keys [on-mouse-down
                                                   on-slot-mouse-down] :as opts}]
  (reify
    om/IRender
    (render [_]
      (let [frame (:frame node)
            title (-> node :node-type :default-title)]
        (html [:div.graph-canvas__node
               {:style (geometry/rectangle->css frame)
                :key (str "node__" node-id)
                :on-mouse-down #(on-mouse-down node-id %)}
               title
               (render-slot-list node-id node on-slot-mouse-down)])))))


(defn prototype-node-component [node-type owner {:keys [on-mouse-down] :as opts}]
  (reify
    om/IRender
    (render [_]
      (let [title (:default-title node-type)
            [width height] (:default-size node-type)]
        (html [:div.prototype-node
               {:style {:width (str width "px")
                        :height (str height "px")}
                :on-mouse-down #(on-mouse-down node-type %)}
               title])))))

