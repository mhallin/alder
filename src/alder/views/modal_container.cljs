(ns alder.views.modal-container
  (:require [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]
            [taoensso.timbre :refer-macros [debug]]
            [cljs.core.async :refer [<! >! chan]]

            [alder.modal :as modal]
            [alder.views.share :refer [share-component]]
            [alder.views.source-export :refer [source-export-component]])

  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]]))

(def modal-component-map
  {:export (fn [] source-export-component)
   :share (fn [] share-component)})

(defn modal-container-component [data owner]
  (let [internal-chan (chan)]
    (reify
      om/IDisplayName
      (display-name [_] "ModalContainer")

      om/IInitState
      (init-state [_]
        {:window-key nil})

      om/IWillMount
      (will-mount [_]
        (go-loop []
          (alt!
            [(:modal-window-chan data)]
            ([msg]
             (when-let [[event value] msg]
               (case event
                 :show (om/set-state! owner :window-key value)
                 :hide (om/set-state! owner :window-key nil))
               (recur)))

            ;; Break go-loop/recur when a message arrives on the internal channel
            internal-chan nil)))

      om/IWillUnmount
      (will-unmount [_]
        (go (>! internal-chan :terminate)))

      om/IRenderState
      (render-state [_ state]
        (if-let [window-key (:window-key state)]
          (let [component ((modal-component-map window-key))]
            (html
             [:div.modal-underlay
              [:div.modal-underlay__dialog
               (om/build component data
                         {:opts {:on-close (partial modal/hide-modal-window data)}})]]))
          (html [:div]))))))
