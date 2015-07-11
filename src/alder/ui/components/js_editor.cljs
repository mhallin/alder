(ns alder.ui.components.js-editor
  (:require [om.core :as om]
            [sablono.core :as html :refer-macros [html]]
            [taoensso.timbre :refer-macros [debug]]

            [alder.node :as node]))

(defn js-editor-component [node owner]
  (letfn [(try-update-node [source node]
            (om/set-state! owner :has-error false)
            (try
              (node/set-input-value node (node/node-input node :source) source)
              (catch js/SyntaxError e
                (om/set-state! owner :has-error true)
                (.info js/console "JS Compilation failed:" e)
                node)
              (catch js/ReferenceError e
                (om/set-state! owner :has-error true)
                (.info js/console "JS Compilation failed:" e)
                node)))

          (on-source-change [event]
            (om/transact! node (partial try-update-node (.-value (.-target event)))))]
    (reify
      om/IDisplayName
      (display-name [_] "JSEditor")

      om/IInitState
      (init-state [_] {:has-error false})

      om/IRenderState
      (render-state [_ state]
        (html
         [:textarea.node-inspector__js-editor
          {:value (node/current-input-value node (node/node-input node :source))
           :class (if (:has-error state) ["m-error"] [])
           :spell-check "false"
           :on-change on-source-change}])))))
