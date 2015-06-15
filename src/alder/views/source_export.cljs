(ns alder.views.source-export
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]

            [alder.graph-export :as graph-export]))

(defn source-export-component [data owner {:keys [on-close]}]
  (reify
    om/IDisplayName
    (display-name [_] "Export")

    om/IRender
    (render [_]
      (html
       [:div.export-component
        [:textarea.export-component__source
         {:value (graph-export/javascript-node-graph (:node-graph data))}]
        [:a
         {:on-click on-close
          :href "#"}
         "Close"]]))))
