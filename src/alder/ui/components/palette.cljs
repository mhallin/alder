(ns alder.ui.components.palette
  (:require [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]
            [taoensso.timbre :refer-macros [debug]]

            [alder.ui.dragging :as dragging]
            [alder.dom-util :as dom-util]
            [alder.geometry :as geometry]
            [alder.math :as math]
            [alder.node :as node]
            [alder.builtin-nodes :as builtin-nodes]
            [alder.node-type :as node-type]
            [alder.ui.components.prototype-node :refer [prototype-node-component]]))

(defn- prototype-node-start-drag [app node-type-id event]
  (when (dom-util/left-button? event)
    (.stopPropagation event)

    (let [mouse-pos (dom-util/event-mouse-pos event)
          mouse-pos (math/mult-point (-> app :graph-xform :inv) mouse-pos)
          elem-pos (geometry/rectangle-origin
                    (dom-util/element-viewport-frame
                     (.-currentTarget event)))
          elem-pos (math/mult-point (-> app :graph-xform :inv) elem-pos)
          offset (geometry/point-sub mouse-pos elem-pos)
          node (builtin-nodes/make-node (:context app)
                                        (geometry/point-sub mouse-pos offset)
                                        node-type-id)]
      (dragging/start-prototype-node-drag app node offset (:prototype-node-drag-chan app)))))

(defn palette-component [data owner]
  (letfn [(render-palette-group [{:keys [title node-types]}]
            (html
             [:div.palette__node-group
              {:key title}
              [:h3.palette__node-group-title
               title]
              (om/build-all prototype-node-component
                            node-types
                            {:opts {:on-mouse-down #(prototype-node-start-drag
                                                     (om/get-props owner)
                                                     %1 %2)}
                             :key 0})]))]
    (reify
      om/IDisplayName
      (display-name [_] "Palette")

      om/IRender
      (render [_]
        (html
         [:div.palette
          [:div.palette__inner
           (map render-palette-group node-type/all-node-groups)]])))))
