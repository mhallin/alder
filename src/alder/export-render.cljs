(ns alder.export-render
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]

            [alder.graph-export :as graph-export]))

(defn export-component [node-graph owner {:keys [on-close]}]
  (reify
    om/IDisplayName
    (display-name [_] "Export")

    om/IRender
    (render [_]
      (html
       [:div.modal-underlay
        [:div.modal-underlay__dialog
         [:div.export-component
          [:textarea.export-component__source
           (graph-export/javascript-node-graph node-graph)]
          [:a
           {:on-click on-close
            :href "#"}
           "Close"]]]]))))
