(ns alder.ui.components.inspector
  (:require [om.core :as om]
            [sablono.core :as html :refer-macros [html]]

            [alder.geometry :as geometry]
            [alder.math :as math]
            [alder.node :as node]
            [alder.node-graph :as node-graph]
            [alder.ui.components.inspector-input :refer [render-input]]
            [alder.ui.components.midi-cc-learn :refer [midi-cc-learn-component]]
            [alder.ui.components.fft-analyser :refer [fft-analyser-component]]
            [alder.ui.components.waveform-analyser :refer [waveform-analyser-component]]
            [alder.ui.components.audio-import :refer [audio-import-component]]))

(def custom-inspector-component
  {:fft (fn [] fft-analyser-component)
   :waveform (fn [] waveform-analyser-component)
   :midi-cc-learn (fn [] midi-cc-learn-component)})

(defn render-inspector-field [node field-type]
  (om/build ((custom-inspector-component field-type))
            node
            {:react-key field-type}))

(defn inspector-component [[node-graph node-id node graph-xform] owner]
  (let [node-origin (-> node :frame geometry/rectangle-origin)
        node-width (-> node :frame :width)
        node-height (-> node :frame :height)
        inspector-width 180
        inspector-origin (geometry/point-add node-origin
                                             [(- (/ node-width 2) (/ inspector-width 2))
                                              (- node-height 4)])
        inspector-origin (math/mult-point (:matrix graph-xform) inspector-origin)
        [inspector-x inspector-y] inspector-origin]
    (letfn [(set-input-value [node input value]
              (om/transact! node
                            (fn [n] (node/set-input-value n input value))))

            (render-input-container [input]
              (html
               [:div.node-inspector__input-container
                {:key (:name input)}
                [:span.node-inspector__input-title
                 (or (:inspector-title input) (:title input))]
                (render-input node input
                              (node/current-input-value node input)
                              #(set-input-value node input %))]))]
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
            (->> (node-graph/editable-inputs node-graph node-id)
                 (map second)
                 (map render-input-container))
            (map #(render-inspector-field node %)
                 (-> node node/node-type :extra-data :inspector-fields))]))))))
