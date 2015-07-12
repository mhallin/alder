(ns alder.ui.components.midi-cc-learn
  (:require [om.core :as om]
            [sablono.core :as html :refer-macros [html]]

            [alder.audio.midiapi :as midiapi]
            [alder.node :as node]))

(defn midi-cc-learn-component [node owner]
  (let [listen-token (.create js/Object #js {})]
    (letfn [(stop-learn []
              (midiapi/remove-midi-message-event-listener (midiapi/node-device
                                                           (node/audio-node node))
                                                          listen-token)
              (om/set-state! owner :is-learning false))

            (on-midi-message [e]
              (let [data (midiapi/event-data e)]
                (when (and (= (.-length data) 3)
                           (= (bit-and 0xf0 (aget data 0)) 0xb0))
                  (let [channel (bit-and 0x7f (aget data 1))
                        channel-input (node/input node :channel)]
                    (om/update! node
                                (node/set-input node channel-input channel))
                    (stop-learn)))))

            (start-learn []
              (midiapi/add-midi-message-event-listener (midiapi/node-device (node/audio-node node))
                                                       listen-token
                                                       on-midi-message)
              (om/set-state! owner :is-learning true))]
      (reify
        om/IDisplayName
        (display-name [_] "MIDICCLearn")

        om/IInitState
        (init-state [_]
          {:is-learning false})

        om/IWillUnmount
        (will-unmount [_]
          (when (:is-learning (om/get-state owner))
            (stop-learn)))

        om/IRenderState
        (render-state [this state]
          (let [is-learning (:is-learning state)]
            (html
             [:div.node-inspector__input-container
              [:span.node-inspector__input-title
               "Learn"]
              [:button.node-inspector__input
               {:on-click (if is-learning stop-learn start-learn)}
               (if is-learning "Stop" "Start")]])))))))
