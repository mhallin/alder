(ns alder.views.inspector
  (:require [om.core :as om]
            [sablono.core :as html :refer-macros [html]]

            [alder.geometry :as geometry]
            [alder.node :as node]
            [alder.node-graph :as node-graph]
            [alder.views.inspector-input :refer [render-input]]
            [alder.views.midi-cc-learn :refer [midi-cc-learn-component]]
            [alder.views.fft-analyser :refer [fft-analyser-component]]
            [alder.views.waveform-analyser :refer [waveform-analyser-component]]
            [alder.views.audio-import :refer [audio-import-component]]))

(def custom-inspector-component
  {:fft (fn [] fft-analyser-component)
   :waveform (fn [] waveform-analyser-component)
   :midi-cc-learn (fn [] midi-cc-learn-component)})

(defn render-inspector-field [node field-type]
  (om/build ((custom-inspector-component field-type)) node))

(defn inspector-component [[node-graph node-id node] owner]
  (let [node-origin (-> node :frame geometry/rectangle-origin)
        node-width (-> node :frame :width)
        node-height (-> node :frame :height)
        inspector-width 180
        inspector-origin (geometry/point-add node-origin
                                             [(- (/ node-width 2) (/ inspector-width 2))
                                              (- node-height 4)])
        [inspector-x inspector-y] inspector-origin]
    (letfn [(set-input-value [node input value]
              (om/update! node
                          (node/set-input-value node input value)))

            (render-input-container [input]
              [:div.node-inspector__input-container
               [:span.node-inspector__input-title
                (or (:inspector-title input) (:title input))]
               (render-input node input
                             (node/current-input-value node input)
                             #(set-input-value node input %))])]
      (reify
        om/IDisplayName
        (display-name [_] "NodeInspector")

        om/IRender
        (render [_]
          (html
           [:div.node-inspector
            {:style {:left (str inspector-x "px")
                     :top (str inspector-y "px")
                     :width (str inspector-width "px")}}
            (->> (node/editable-inputs node)
                 (node-graph/disconnected-inputs node-graph node-id)
                 (map second)
                 (map render-input-container))
            (map #(render-inspector-field node %)
                 (-> node node/node-type :extra-data :inspector-fields))]))))))
