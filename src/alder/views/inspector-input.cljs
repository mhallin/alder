(ns alder.views.inspector-input
  (:require [om.core :as om]
            [sablono.core :as html :refer-macros [html]]
            [taoensso.timbre :refer-macros [debug]]

            [alder.audio.midiapi :as midiapi]))

(defn render-choice-input [input value on-change]
  [:select.node-inspector__input
   {:value value
    :on-change #(on-change (-> % .-target .-value))}
   (map (fn [val] [:option
                   {:value val}
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

(defn render-number-input [input value on-change]
  (let [[min max] (:range input)]
    (om/build number-input-component value
              {:opts {:min min
                      :max max
                      :on-change on-change}})))

(defn render-string-input [input value on-change]
  [:input.node-inspector__input
   {:value value
    :on-change #(on-change (-> % .-target .-value))}])

(defn render-gate-input [input value on-change]
  [:button.node-inspector__input
   {:on-mouse-down #(on-change 1.0)
    :on-mouse-up #(on-change 0.0)}
   "Trig"])

(defn midi-iterator->map [iterator]
  (let [value (.next iterator)]
    (if (and value (not (.-done value)))
      (conj (midi-iterator->map iterator)
            [(aget (.-value value) 0) (aget (.-value value) 1)])
      {})))

(defn midi-device-input-component [[input value on-change] owner]
  (letfn [(devices-loaded [devices]
            (let [inputs (aget devices "inputs")
                  entries (.call (aget inputs "entries") inputs)]
              (om/set-state! owner :inputs
                             (midi-iterator->map entries))))]
    (reify
      om/IDisplayName
      (display-name [_] "MidiDeviceInput")

      om/IWillMount
      (will-mount [_]
        (.then (midiapi/request-midi-access) devices-loaded))

      om/IRender
      (render [_]
        (html
         [:select.node-inspector__input
          {:on-change (fn [event] (let [inputs (om/get-state owner :inputs)
                                        id (.-value (.-target event))]
                                    (on-change (inputs id))))
           :value (when value (.-id value))}
          [:option {:value nil} "(no input)"]
          (map (fn [[id input]] [:option {:value id} (.-name input)])
               (om/get-state owner :inputs))])))))

(defn render-midi-device-input [input value on-change]
  (om/build midi-device-input-component [input value on-change]))

(defn render-boolean-input [input value on-change]
  (html
   [:input.node-inspector__checkbox-input
    {:type "checkbox"
     :checked value
     :on-change #(on-change (.-checked (.-target %)))}]))

(defn render-input [input value on-change]
  (let [render-fn (cond (:choices input) render-choice-input
                        (= (:data-type input) :number) render-number-input
                        (= (:type input) :gate) render-gate-input
                        (= (:data-type input) :midi-device) render-midi-device-input
                        (= (:data-type input) :boolean) render-boolean-input
                        :else render-string-input)]
    (render-fn input value on-change)))
