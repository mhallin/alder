(ns alder.ui.components.prototype-node
  (:require [om.core :as om]
            [sablono.core :as html :refer-macros [html]]))

(defn prototype-node-component [[node-type-id node-type] owner {:keys [on-mouse-down]}]
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
                :on-mouse-down #(on-mouse-down node-type-id %)}
               title])))))
