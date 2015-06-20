(ns alder.ui.components.waveform-analyser
  (:require [om.core :as om]
            [sablono.core :as html :refer-macros [html]]
            [clojure.string :as string]

            [alder.audio.aapi :as aapi]))

(def fft-size 256)

(defn waveform-analyser-component [node owner]
  (let [width 160
        height 160
        data-array (js/Uint8Array. fft-size)
        analyser-node (:audio-node node)]
    (aapi/set-fft-size! analyser-node fft-size)
    (letfn [(update-waveform-data []
              (aapi/get-byte-time-domain-data analyser-node data-array)
              (let [steps (.-length data-array)
                    step-width (/ width steps)
                    line-segments (map (fn [i]
                                         (let [v (aget data-array i)
                                               x (* i step-width)
                                               y (+ (/ height 2)
                                                    (* (- 1 (/ v 128)) (/ height 2)))]
                                           (str (if (zero? i) "M" "L")
                                                x "," y " ")))
                                       (range steps))
                    line-data (string/join line-segments)]
                (om/set-state! owner :line-data line-data)))

            (tick-animation []
              (update-waveform-data)
              (when (om/get-state owner :is-mounted)
                (.requestAnimationFrame js/window tick-animation)))]
      (reify
        om/IDisplayName
        (display-name [_] "WaveformAnalyser")

        om/IWillMount
        (will-mount [_]
          (om/set-state! owner :is-mounted true))

        om/IDidMount
        (did-mount [_]
          (.requestAnimationFrame js/window tick-animation))

        om/IWillUnmount
        (will-unmount [_]
          (om/set-state! owner :is-mounted false))

        om/IRender
        (render [_]
          (when-let [line-data (om/get-state owner :line-data)]
            (html
             [:svg.node-inspector__waveform
              {:style {:width (str width "px")
                       :height (str height "px")}}
              [:path.node-inspector__waveform-line
               {:d line-data}]])))))))
