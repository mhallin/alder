(ns alder.views.share
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [taoensso.timbre :refer-macros [debug]]

            [alder.comm :as comm])

  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn share-component [data owner {:keys [on-close]}]
  (reify
    om/IDisplayName
    (display-name [_]
      "Share")

    om/IWillMount
    (will-mount [_]
      (go
        (let [short-id (-> data :current-page-args :short-id)
              chan (comm/create-readonly-duplicate-patch short-id)
              reply (<! chan)]
          (debug "Got reply" reply)
          (when-let [[_ {:keys [short-id]}] reply]
            (om/set-state! owner :share-url
                           (str (.-origin js/location)
                                "/"
                                short-id))))))

    om/IRenderState
    (render-state [_ state]
      (html
       [:div.share-component
        [:p "Copy this link to share a snapshot of the current patch:"]
        [:input.share-component__link-field
         {:type :text
          :readonly true
          :value (:share-url state)}]
        [:p
         [:a
          {:on-click on-close
           :href "#"}
          "Close"]]]))))
