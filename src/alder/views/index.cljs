(ns alder.views.index
  (:require [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]
            [taoensso.timbre :refer-macros [debug]]

            [alder.comm :as comm]
            [alder.routes :as routes])

  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn index-component [data owner]
  (reify
    om/IDisplayName
    (display-name [_] "Index")

    om/IWillMount
    (will-mount [_]
      (debug "Index component creating new patch")
      (let [reply-chan (comm/create-new-patch)]
        (go
          (let [[_ {:keys [short-id]}] (<! reply-chan)]
            (routes/replace-navigation! (routes/show-patch {:short-id short-id}))))))

    om/IRender
    (render [_]
      (html [:div.alder-index
             [:h1 "Alder DSP Editor"]
             [:p "Loading initial patch..."]]))))
