(ns alder.ui.components.inspector-input
  (:require [om.core :as om]
            [sablono.core :as html :refer-macros [html]]
            [taoensso.timbre :refer-macros [debug]]

            [alder.audio.midiapi :as midiapi]
            [alder.ui.components.audio-import :refer [audio-import-component]]
            [alder.ui.components.midi-device-input :refer [midi-device-input-component]]))

(defn render-choice-input [node input value on-change]
  [:select.node-inspector__input
   {:value value
    :on-change #(on-change (-> % .-target .-value))}
   (map (fn [val] [:option
                   {:key val :value val}
                   val])
        (:choices input))])

(defn number-input-component [value owner {:keys [min max on-change]}]
  (letfn [(parse-float [v]
            (when (.test #"^(\-|\+)?[0-9]+(\.[0-9]+)?$" v)
              (js/parseFloat v)))

          (input-did-change [e]
            (let [new-value (-> e .-target .-value)]
              (om/set-state! owner :value new-value)
              (when-let [f (parse-float new-value)]
                (on-change f))))]
    (reify
      om/IDisplayName
      (display-name [_] "NumberInput")

      om/IInitState
      (init-state [_] {:value (str value)
                       :init-value value})

      om/IWillReceiveProps
      (will-receive-props [_ next-value]
        (let [init-value (:init-value (om/get-state owner))]
          (when (not= init-value next-value)
            (om/set-state! owner {:value (str next-value)
                                  :init-value value}))))

      om/IRenderState
      (render-state [_ state]
        (html
         [:input.node-inspector__input
          {:type "number"
           :step "any"
           :value (:value state)
           :min min
           :max max
           :on-change input-did-change}])))))

(defn render-number-input [node input value on-change]
  (let [[min max] (:range input)]
    (om/build number-input-component value
              {:opts {:min min
                      :max max
                      :on-change on-change}})))

(defn render-string-input [node input value on-change]
  [:input.node-inspector__input
   {:value value
    :on-change #(on-change (-> % .-target .-value))}])

(defn render-gate-input [node input value on-change]
  [:button.node-inspector__input
   {:on-mouse-down #(on-change 1.0)
    :on-mouse-up #(on-change 0.0)}
   "Trig"])

(defn render-midi-device-input [_ _ value on-change]
  (om/build midi-device-input-component [value on-change]
            {:opts {:class "node-inspector__input"
                    :include-master true}}))

(defn render-boolean-input [node input value on-change]
  (html
   [:input.node-inspector__checkbox-input
    {:type "checkbox"
     :checked value
     :on-change #(on-change (.-checked (.-target %)))}]))

(defn render-buffer-input [node input value on-change]
  (om/build audio-import-component [node input value on-change]))

(defn render-input [node input value on-change]
  (let [render-fn (cond (:choices input) render-choice-input
                        (= (:data-type input) :number) render-number-input
                        (= (:type input) :gate) render-gate-input
                        (= (:data-type input) :midi-device) render-midi-device-input
                        (= (:data-type input) :boolean) render-boolean-input
                        (= (:data-type input) :buffer) render-buffer-input
                        :else render-string-input)]
    (render-fn node input value on-change)))
