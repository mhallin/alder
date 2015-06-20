(ns alder.views.root
  (:require [om.core :as om]
            [sablono.core :refer-macros [html]]

            [alder.views.index :refer [index-component]]
            [alder.views.editor :refer [editor-component]]))

(defn root-component [data owner]
  (reify
    om/IDisplayName
    (display-name [_] "AlderRoot")

    om/IRender
    (render [_]
      (case (:current-page data)
        :index (om/build index-component data)
        :show-patch (om/build editor-component data)
        :none (html [:div])))))
