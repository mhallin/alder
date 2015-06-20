(ns alder.ui.components.fft-analyser
  (:require [om.core :as om]
            [sablono.core :as html :refer-macros [html]]
            [clojure.string :as string]

            [alder.audio.aapi :as aapi]))

(def fft-size 256)

(defn fft-analyser-component [node owner]
  (aapi/set-fft-size! (:audio-node node) fft-size)
  (let [width 160
        height 100
        noise-floor 90
        analyser-node (:audio-node node)
        data-array (js/Float32Array. (aapi/frequency-bin-count analyser-node))]
    (letfn [(update-frequency-data []
              (aapi/get-float-frequency-data analyser-node data-array)
              (let [bins (.-length data-array)
                    bin-width (/ width bins)
                    line-segments (map (fn [i]
                                         (let [v (aget data-array i)
                                               h (js/Math.round
                                                  (* height
                                                     (/ (max 0 (+ noise-floor v))
                                                        noise-floor)))
                                               x (* i bin-width)
                                               y (- height h)]
                                           (str (if (zero? i) "M" "L")
                                                x "," y " ")))
                                       (range bins))
                    line-data (string/join line-segments)]
                (om/set-state! owner :line-data line-data)))

            (tick-animation []
              (update-frequency-data)
              (when (om/get-state owner :is-mounted)
                (.requestAnimationFrame js/window tick-animation)))]

      (reify
        om/IDisplayName
        (display-name [_] "FFTAnalyser")

        om/IWillMount
        (will-mount [_]
          (om/set-state! owner :is-mounted true)
          (update-frequency-data))

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
             [:svg.node-inspector__fft
              {:style {:width (str width "px")
                       :height (str height "px")}}
              [:path.node-inspector__fft-line
               {:d line-data}]])))))))
