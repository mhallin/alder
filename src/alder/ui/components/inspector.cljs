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
            [alder.ui.components.audio-import :refer [audio-import-component]]
            [alder.ui.components.js-editor :refer [js-editor-component]]))

(def custom-inspector-component
  {:fft (fn [] fft-analyser-component)
   :waveform (fn [] waveform-analyser-component)
   :midi-cc-learn (fn [] midi-cc-learn-component)
   :js-editor (fn [] js-editor-component)})

(defn render-inspector-field [node field-type]
  (om/build ((custom-inspector-component field-type))
            node
            {:react-key field-type}))

(defn inspector-component [[node-graph node-id node graph-xform] owner]
  (let [node-origin (-> node node/frame geometry/rectangle-origin)
        node-width (-> node node/frame :width)
        node-height (-> node node/frame :height)
        inspector-width (or (-> node node/inspector-data :inspector-width) 180)
        inspector-origin (geometry/point-add node-origin
                                             [(- (/ node-width 2) (/ inspector-width 2))
                                              (- node-height 4)])
        inspector-origin (math/mult-point (:matrix graph-xform) inspector-origin)
        [inspector-x inspector-y] inspector-origin]
    (letfn [(set-input [node input value]
              (om/transact! node
                            (fn [n] (node/set-input n input value))))

            (render-input-container [input]
              (html
               [:div.node-inspector__input-container
                {:key (:name input)}
                [:span.node-inspector__input-title
                 (or (:inspector-title input) (:title input))]
                (render-input node input
                              (node/current-input-value node input)
                              #(set-input node input %))]))]
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
                 (remove (fn [[id _]]
                           (when-let [hide-fields (-> (node/inspector-data node)
                                                      :inspector-hide-fields)]
                             (hide-fields id))))
                 (map second)
                 (map render-input-container))
            (map #(render-inspector-field node %)
                 (-> node node/inspector-data :inspector-fields))]))))))
