(ns alder.node-render
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]

            [alder.node :as node]
            [alder.geometry :as geometry]))

(defn render-choice-input [input value on-change]
  [:select.node-inspector__input
   {:value value
    :on-change #(on-change (-> % .-target .-value))}
   (map (fn [val] [:option
                   {:value val}
                   val])
        (:choices input))])

(defn render-number-input [input value on-change]
  (let [[min max] (:range input)]
    [:input.node-inspector__input
     {:type :number
      :step :any
      :value value
      :min min
      :max max
      :on-change #(on-change (-> % .-target .-value js/parseFloat))}]))

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
            (om/set-state! owner :inputs
                           (-> devices .-inputs .entries midi-iterator->map)))]
    (reify
      om/IDisplayName
      (display-name [_] "MidiDeviceInput")

      om/IWillMount
      (will-mount [_]
        (.then (.requestMIDIAccess js/navigator) devices-loaded))

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
  (let [width 160
        height 100
        noise-floor 90
        fft-size 256
        data-array (js/Float32Array. (.-frequencyBinCount analyser-node))]
    (letfn [(update-frequency-data []
              (.getFloatFrequencyData analyser-node data-array)
              (let [bins (.-length data-array)
                    bin-width (/ width bins)
                    bars (map (fn [i]
                                (let [v (aget data-array i)
                                      h (js/Math.round
                                         (* height (/ (max 0 (+ noise-floor v)) noise-floor)))
                                      x (* i bin-width)
                                      y (- height h)]
                                  [x y bin-width h]))
                              (range bins))]
                (om/set-state! owner :graph-bars bars)))

            (tick-animation []
              (update-frequency-data)
              (when (om/get-state owner :is-mounted)
                (.requestAnimationFrame js/window tick-animation)))]

      (reify
        om/IDisplayName
        (display-name [_] "FFTComponent")

        om/IWillMount
        (will-mount [_]
          (set! (.-fftSize analyser-node) 256)
          (om/set-state! owner :is-mounted true)
          (update-frequency-data))

        om/IDidMount
        (did-mount [_]
          (.requestAnimationFrame js/window tick-animation))

        om/IWillUnmount
        (will-unmount [_]
          (om/set-state! owner :s-mounted false))

        om/IRender
        (render [_]
          (when-let [graph-bars (om/get-state owner :graph-bars)]
            (let []
              (html
               [:svg.node-inspector__fft
                {:style {:width (str width "px")
                         :height (str height "px")}}
                (map-indexed (fn [i [x y w h]]
                               [:rect
                                {:x x :y y :width w :height h :key i}])
                             graph-bars)]))))))))

(defn render-inspector-field [node field-type]
  (case field-type
    :fft (om/build fft-component (:audio-node node))))

(defn inspector-component [[node-id node] owner]
  (let [node-origin (-> node :frame geometry/rectangle-origin)
        node-width (-> node :frame :width)
        node-height (-> node :frame :height)
        inspector-width 180
        inspector-origin (geometry/point-add node-origin
                                             [(- (/ node-width 2) (/ inspector-width 2))
                                              (- node-height 4)])
        [inspector-x inspector-y] inspector-origin]
    (letfn [(set-input-value [node input value]
              (node/set-input-value node input value)
              (om/refresh! owner))

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
            (map render-input-container
                 (node/editable-inputs node))
            (map #(render-inspector-field node %)
                 (-> node :node-type :extra-data :inspector-fields))]))))))


(defn- render-slot-list [node-id node on-mouse-down]
  (let [slot-frames (node/node-slot-frames node)]
    (map (fn [[slot-id [slot slot-frame]]]
           [:div.graph-canvas__node-slot
            {:style (geometry/rectangle->css slot-frame)
             :title (:title slot)
             :on-mouse-down #(on-mouse-down node-id slot-id %)}])
         slot-frames)))


(defn node-component [[node-id node] owner {:keys [on-mouse-down
                                                   on-slot-mouse-down] :as opts}]
  (reify
    om/IDisplayName
    (display-name [_] "Node")
    
    om/IRender
    (render [_]
      (let [frame (:frame node)
            title (-> node :node-type :default-title)]
        (html [:div.graph-canvas__node
               {:style (geometry/rectangle->css frame)
                :key (str "node__" node-id)
                :on-mouse-down #(on-mouse-down node-id %)}
               title
               [:img.graph-canvas__node-inspector-toggle
                {:src (if (:inspector-visible node)
                        "/img/inspector_close_button.svg"
                        "/img/inspector_open_button.svg")
                 :on-click #(om/transact! node :inspector-visible (fn [x] (not x)))}]
               (render-slot-list node-id node on-slot-mouse-down)])))))


(defn prototype-node-component [node-type owner {:keys [on-mouse-down] :as opts}]
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
                :on-mouse-down #(on-mouse-down node-type %)}
               title])))))

