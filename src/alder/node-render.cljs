(ns alder.node-render
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]

            [alder.node :as node]
            [alder.geometry :as geometry]))

(defn render-choice-input [input value on-change]
  [:select.node-inspector__input
   {:value value
    :on-change on-change}
   (map (fn [val] [:option
                   {:value val}
                   val])
        (:choices input))])

(defn render-number-input [input value on-change]
  (let [[min max] (:range input)]
    [:input.node-inspector__input
     {:type :number
      :step :any
      :value value
      :min min
      :max max
      :on-change on-change}]))

(defn render-string-input [input value on-change]
  [:input.node-inspector__input
   {:value value
    :on-change on-change}])

(defn render-input [input value on-change]
  (let [render-fn (cond (:choices input) render-choice-input
                        (= (:data-type input) :number) render-number-input
                        :else render-string-input)]
    (render-fn input value on-change)))

(defn inspector-component [[node-id node] owner]
  (let [node-origin (-> node :frame geometry/rectangle-origin)
        node-width (-> node :frame :width)
        node-height (-> node :frame :height)
        inspector-width 180
        inspector-origin (geometry/point-add node-origin
                                             [(- (/ node-width 2) (/ inspector-width 2))
                                              (- node-height 4)])
        [inspector-x inspector-y] inspector-origin]
    (letfn [(set-input-value [node input value]
              (node/set-input-value node input value)
              (om/refresh! owner))

            (render-input-container [input]
              [:div.node-inspector__input-container
               [:span.node-inspector__input-title
                (or (:inspector-title input) (:title input))]
               (render-input input
                                 (node/current-input-value node input)
                                 #(set-input-value node input (.-value (.-target %))))])]
      (reify
        om/IDisplayName
        (display-name [_] "NodeInspector")
        
        om/IRender
        (render [_]
          (html
           [:div.node-inspector
            {:style {:left (str inspector-x "px")
                     :top (str inspector-y "px")
                     :width (str inspector-width "px")}}
            (map render-input-container
                 (node/editable-inputs node))]))))))


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
    om/IDisplayName
    (display-name [_] "Node")
    
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
    om/IDisplayName
    (display-name [_] "PrototypeNode")
    
    om/IRender
    (render [_]
      (let [title (:default-title node-type)
            [width height] (:default-size node-type)]
        (html [:div.prototype-node
               {:style {:width (str width "px")
                        :height (str height "px")}
                :on-mouse-down #(on-mouse-down node-type %)}
               title])))))

