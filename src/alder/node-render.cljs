(ns alder.node-render
  (:require [clojure.string :as string]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]

            [alder.node-graph :as node-graph]
            [alder.node :as node]
            [alder.geometry :as geometry]
            [alder.audio.aapi :as aapi]
            [alder.audio.midiapi :as midiapi])

  (:require-macros [taoensso.timbre :refer [debug]]))

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

(defn render-input [input value on-change]
  (let [render-fn (cond (:choices input) render-choice-input
                        (= (:data-type input) :number) render-number-input
                        (= (:type input) :gate) render-gate-input
                        (= (:data-type input) :midi-device) render-midi-device-input
                        :else render-string-input)]
    (render-fn input value on-change)))

(defn fft-component [analyser-node owner]
  (let [fft-size 256]
    (aapi/set-fft-size! analyser-node fft-size)
    (let [width 160
          height 100
          noise-floor 90
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
                 {:d line-data}]]))))))))

(defn waveform-component [analyser-node owner]
  (let [fft-size 256]
    (aapi/set-fft-size! analyser-node fft-size)
    (let [width 160
          height 160
          data-array (js/Uint8Array. fft-size)]
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
                 {:d line-data}]]))))))))

(defn midi-cc-learn-component [node owner]
  (let [listen-token (.create js/Object #js {})]
    (letfn [(stop-learn []
              (midiapi/remove-midi-event-listener (.device (:audio-node node))
                                                  listen-token)
              (om/set-state! owner :is-learning false))

            (on-midi-message [e]
              (let [data (.-data e)]
                (when (and (= (.-length data) 3)
                           (= (bit-and 0xf0 (aget data 0)) 0xb0))
                  (let [channel (bit-and 0x7f (aget data 1))
                        channel-input (:channel (:inputs (node/node-type node)))]
                    (om/update! node
                                (node/set-input-value node channel-input channel))
                    (stop-learn)))))

            (start-learn []
              (midiapi/add-midi-event-listener (.device (:audio-node node))
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

(defn render-inspector-field [node field-type]
  (case field-type
    :fft (om/build fft-component (:audio-node node))
    :waveform (om/build waveform-component (:audio-node node))
    :midi-cc-learn (om/build midi-cc-learn-component node)))

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
               (render-input input
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


(defn- render-slot [node-id node slot-id slot slot-frame slot-drag-data on-mouse-down]
  (html
   [:div.graph-canvas__node-slot
    {:style (geometry/rectangle->css slot-frame)
     :class (if slot-drag-data
              [(if (node/can-connect slot-drag-data [node slot-id])
                 "m-connectable"
                 "m-not-connectable")]
              [])
     :title (:title slot)
     :on-mouse-down #(on-mouse-down node-id slot-id %)}]))

(defn- render-slot-list [node-id node slot-drag-data on-mouse-down]
  (let [slot-frames (node/node-slot-frames node)]
    (map (fn [[slot-id [slot slot-frame]]]
           (render-slot node-id node slot-id slot slot-frame slot-drag-data on-mouse-down))
         slot-frames)))

(defn node-component [[node-id node slot-drag-data selection] owner
                      {:keys [on-mouse-down on-slot-mouse-down] :as opts}]
  (reify
    om/IDisplayName
    (display-name [_] "Node")

    om/IRender
    (render [_]
      (let [frame (:frame node)
            title (-> node node/node-type :default-title)]
        (html [:div.graph-canvas__node
               {:style (geometry/rectangle->css frame)
                :key (str "node__" node-id)
                :on-mouse-down #(on-mouse-down node-id %)
                :class (if (selection node-id) ["m-selected"] [])}
               title
               [:div.graph-canvas__node-inspector-toggle
                {:class (if (:inspector-visible node) "m-open" "m-closed")
                 :on-click #(om/transact! node :inspector-visible (fn [x] (not x)))}]
               (render-slot-list node-id node slot-drag-data on-slot-mouse-down)])))))


(defn prototype-node-component [[node-type-id node-type] owner {:keys [on-mouse-down]}]
  (reify
    om/IDisplayName
    (display-name [_] "PrototypeNode")

    om/IRender
    (render [_]
      (let [title (:default-title node-type)
            [width height] (:default-size node-type)]
        (html [:div.prototype-node
               {:style {:width (str width "px")
                        :height (str height "px")}
                :on-mouse-down #(on-mouse-down node-type-id %)}
               title])))))
