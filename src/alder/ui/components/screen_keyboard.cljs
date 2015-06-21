(ns alder.ui.components.screen-keyboard
  (:require [om.core :as om]
            [sablono.core :refer-macros [html]]
            [taoensso.timbre :refer-macros [debug]]

            [alder.audio.midiapi :as midiapi]))

;;  W E   T Y U   O P
;; A S D F G H J K L :
(def keyboard-chars "AWSEDFTGYHUJKOLP:")

(defn- index->key-name [root index]
  (aget keyboard-chars (- index root)))

(defn- key-name->index [root key-name]
  (let [index (.indexOf keyboard-chars key-name)]
    (if (>= index 0)
      (+ root index)
      nil)))

(defn screen-keyboard-component [_ owner]
  (let [white-key-offsets #{0 2 4 5 7 9 11 12 14 16}
        black-key-offsets #{1 3 6 8 10 13 15}]
    (letfn [(on-key-mouse-down [index e]
              (midiapi/emit-midi-master-device-event
               {:data [0x90 index 0x64]}))

            (on-key-mouse-up [index e]
              (midiapi/emit-midi-master-device-event
               {:data [0x80 index 0x00]}))

            (on-key-down [e]
              (when-not (.-repeat e)
                (when-let [index (key-name->index (om/get-state owner :root-note)
                                                  (char (.-keyCode e)))]
                  (on-key-mouse-down index e))))

            (on-key-up [e]
              (when-let [index (key-name->index (om/get-state owner :root-note)
                                                (char (.-keyCode e)))]
                (on-key-mouse-up index e)))

            (render-key [root relative-index]
              (let [index (+ root relative-index)]
                (html
                 [:span
                  {:key index
                   :class (if (black-key-offsets relative-index)
                            "screen-keyboard__black-key"
                            "screen-keyboard__white-key")
                   :on-mouse-down (partial on-key-mouse-down index)
                   :on-mouse-up (partial on-key-mouse-up index)}
                  (index->key-name root index)])))]
      (reify
        om/IDisplayName
        (display-name [_] "ScreenKeyboard")

        om/IInitState
        (init-state [_]
          {:root-note 60})

        om/IWillMount
        (will-mount [_]
          (.addEventListener js/window "keydown" on-key-down)
          (.addEventListener js/window "keyup" on-key-up))

        om/IWillUnmount
        (will-unmount [_]
          (.removeEventListener js/window "keydown" on-key-down)
          (.removeEventListener js/window "keyup" on-key-up))

        om/IRenderState
        (render-state [_ state]
          (let [{:keys [root-note]} state]
            (html
             [:div.screen-keyboard
              (map (partial render-key root-note)
                   (range 17))])))))))
