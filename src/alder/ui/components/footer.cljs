(ns alder.ui.components.footer
  (:require [om.core :as om]
            [sablono.core :refer-macros [html]]
            [taoensso.timbre :refer-macros [debug]]

            [alder.audio.midiapi :as midiapi]
            [alder.ui.components.midi-device-input :refer [midi-device-input-component]]))

(defn footer-component [data owner]
  (letfn [(toggle-screen-keyboard [e]
            (.preventDefault e)
            (om/transact! data :screen-keyboard-visible not))]
    (reify
      om/IDisplayName
      (display-name [_] "Footer")

      om/IInitState
      (init-state [_]
        {:screen-keyboard-visible false})

      om/IRenderState
      (render-state [_ state]
        (html
         [:div.footer
          (when (midiapi/has-midi-access)
            (html
             [:div.footer__midi-master
              [:span.footer__midi-master-label "MIDI Master Device"]
              (om/build midi-device-input-component
                        [(midiapi/get-current-midi-master-device)
                         midiapi/set-current-midi-master-device!]
                        {:opts {:class "footer__midi-master-dropdown"}})]))
          [:a.footer__aux-button
           {:on-click toggle-screen-keyboard
            :href "#"}
           "Keyboard"]])))))
