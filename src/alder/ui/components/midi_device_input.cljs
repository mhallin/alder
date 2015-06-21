(ns alder.ui.components.midi-device-input
  (:require [om.core :as om]
            [sablono.core :refer-macros [html]]

            [alder.audio.midiapi :as midiapi]))

(defn- midi-iterator->map [iterator]
  (let [value (.next iterator)]
    (if (and value (not (.-done value)))
      (conj (midi-iterator->map iterator)
            [(aget (.-value value) 0) (aget (.-value value) 1)])
      {})))

(defn midi-device-input-component [[value on-change] owner {:keys [class include-master]}]
  (letfn [(update-inputs [inputs]
            (let [entries (.call (aget inputs "entries") inputs)
                  entry-map (midi-iterator->map entries)
                  master-device (midiapi/midi-master-device)
                  entry-map-with-master (assoc entry-map
                                               (aget master-device "id")
                                               master-device)]
              (om/set-state! owner :inputs
                             (if include-master
                               entry-map-with-master
                               entry-map))))

          (on-state-change [event]
            (let [access (om/get-state owner :midi-access)]
              (update-inputs (aget access "inputs"))))]
    (reify
      om/IDisplayName
      (display-name [_] "MidiDeviceInput")

      om/IInitState
      (init-state [_]
        (let [master-device (midiapi/midi-master-device)]
          {:inputs {(aget master-device "id") master-device}}))

      om/IWillMount
      (will-mount [_]
        (when (midiapi/has-midi-access)
          (.then (midiapi/request-midi-access)
                 (fn [access]
                   (om/set-state! owner :midi-access access)
                   (update-inputs (aget access "inputs"))
                   (.addEventListener access "statechange" on-state-change)))))

      om/IWillUnmount
      (will-unmount [_]
        (when-let [access (om/get-state owner :midi-access)]
          (.removeEventListener access "statechange" on-state-change)))

      om/IRender
      (render [_]
        (html
         [:select
          {:on-change (fn [event] (let [inputs (om/get-state owner :inputs)
                                        id (.-value (.-target event))]
                                    (on-change (inputs id))))
           :class class
           :value (when value (.-id value))}
          [:option {:value nil} "(no input)"]
          (map (fn [[id input]] [:option {:value id :key id} (.-name input)])
               (sort (om/get-state owner :inputs)))])))))
