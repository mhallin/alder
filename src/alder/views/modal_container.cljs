(ns alder.views.modal-container
  (:require [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]

            [alder.modal :as modal]
            [alder.views.share :refer [share-component]]
            [alder.views.source-export :refer [source-export-component]])

  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(def modal-component-map
  {:export (fn [] source-export-component)
   :share (fn [] share-component)})

(defn modal-container-component [data owner]
  (reify
    om/IDisplayName
    (display-name [_] "ModalContainer")

    om/IInitState
    (init-state [_]
      {:window-key nil})

    om/IWillMount
    (will-mount [_]
      (go-loop []
        (when-let [[event value] (<! (:modal-window-chan data))]
          (case event
            :show (om/set-state! owner :window-key value)
            :hide (om/set-state! owner :window-key nil))
          (recur))))

    om/IRenderState
    (render-state [_ state]
      (if-let [window-key (:window-key state)]
        (let [component ((modal-component-map window-key))]
          (html
           [:div.modal-underlay
            [:div.modal-underlay__dialog
             (om/build component data
                       {:opts {:on-close (partial modal/hide-modal-window data)}})]]))
        (html [:div])))))
