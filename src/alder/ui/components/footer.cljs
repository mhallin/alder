(ns alder.ui.components.footer
  (:require [om.core :as om]
            [sablono.core :refer-macros [html]]
            [taoensso.timbre :refer-macros [debug]]

            [alder.audio.midiapi :as midiapi]
            [alder.ui.components.midi-device-input :refer [midi-device-input-component]]))

(defn footer-component [data owner]
  (letfn [(on-state-change [e]
            (debug "State change" e))]
    (reify
      om/IDisplayName
      (display-name [_] "Footer")

      om/IRenderState
      (render-state [_ state]
        (html
         [:div.footer
          (when (midiapi/has-midi-access)
            [:div.footer__midi-master
             [:span.footer__midi-master-label "MIDI Master Device"]
             (om/build midi-device-input-component
                       [(midiapi/get-current-midi-master-device)
                        midiapi/set-current-midi-master-device!]
                       {:opts {:class "footer__midi-master-dropdown"}})])])))))
