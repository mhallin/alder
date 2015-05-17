(ns alder.node
  (:require [alder.geometry :as geometry]
            [alder.audio.adsr :as adsr]
            [alder.audio.midi :as midi]
            [alder.audio.midiapi :as midiapi]))

(defrecord NodeType
    [inputs outputs extra-data built-in default-title default-size constructor])

(defrecord Node
    [frame node-type audio-node])

(def audio-destination-node-type
  (NodeType. {:signal {:type :node
                       :index 0
                       :title "Signal"}}
             {}
             nil
             true
             "Audio Out"
             [110 40]
             (fn [ctx] (aget ctx "destination"))))

(def oscillator-node-type
  (NodeType. {:frequency {:type :param
                          :name "frequency"
                          :default 220
                          :title "Frequency"
                          :data-type :number
                          :range [0 22050]}
              :waveform {:type :constant
                         :name "type"
                         :default "square"
                         :title "Waveform"
                         :data-type :string
                         :choices ["sine" "square" "sawtooth" "triangle"]}}
             {:signal {:type :node
                       :index 0
                       :title "Signal"}}
             nil
             false
             "Osc"
             [70 40]
             (fn [ctx] (.call (aget ctx "createOscillator") ctx))))

(def gain-node-type
  (NodeType. {:gain {:type :param
                     :name "gain"
                     :default 1
                     :title "Gain"
                     :data-type :number}
              :signal-in {:type :node
                          :index 0
                          :title "Signal"}}
             {:signal-out {:type :node
                           :index 0
                           :title "Signal"}}
             nil
             false
             "Gain"
             [70 40]
             (fn [ctx] (.call (aget ctx "createGain") ctx))))

(def adsr-node-type
  (NodeType. {:gate {:type :gate
                     :name "gate"
                     :title "Gate"}
              :attack {:type :constant
                       :name "attack"
                       :default 0.1
                       :title "Attack"
                       :data-type :number}
              :decay {:type :constant
                      :name "decay"
                      :default 0.1
                      :title "Decay"
                      :data-type :number}
              :sustain {:type :constant
                        :name "sustain"
                        :default 0.8
                        :title "Sustain"
                        :data-type :number}
              :release {:type :constant
                        :name "release"
                        :default 0.1
                        :title "Release"
                        :data-type :number}}
             {:envelope {:type :node
                         :index 0
                         :title "Envelope"}}
             nil
             false
             "ADSR"
             [70 90]
             #(adsr/make-adsr-node %)))

(def fft-analyser-node-type
  (NodeType. {:signal-in {:type :node
                          :index 0
                          :title "Signal"}}
             {:signal-out {:type :node
                           :index 0
                           :title "Signal"}}
             {:inspector-fields [:fft]}
             false
             "FFT"
             [70 40]
             (fn [ctx] (.call (aget ctx "createAnalyser") ctx))))

(def scope-analyser-node-type
  (NodeType. {:signal-in {:type :node
                          :index 0
                          :title "Signal"}}
             {:signal-out {:type :node
                           :index 0
                           :title "Signal"}}
             {:inspector-fields [:waveform]}
             false
             "Scope"
             [80 40]
             (fn [ctx] (.call (aget ctx "createAnalyser") ctx))))

(def midi-note-node-type
  (NodeType. {:device {:type :accessor
                       :name "device"
                       :default nil
                       :title "Device"
                       :data-type :midi-device}}
             {:gate {:type :node
                     :index 0
                     :title "Gate"}
              :frequency {:type :node
                          :index 1
                          :title "Frequency"}}
             nil
             false
             "MIDI Note"
             [110 40]
             #(midi/make-midi-note-node %)))

(def has-midi-support (midiapi/has-midi-access))

(def all-node-types
  (let [basic-nodes [audio-destination-node-type
                     oscillator-node-type
                     gain-node-type
                     adsr-node-type
                     fft-analyser-node-type
                     scope-analyser-node-type]
        midi-nodes (if has-midi-support
                     [midi-note-node-type]
                     [])]
    (concat basic-nodes midi-nodes)))


(defn- assign-default-node-inputs [node]
  (let [audio-node (:audio-node node)]
   (doseq [[_ input] (-> node :node-type :inputs)]
     (let [input-name (:name input)
           input-type (:type input)
           default-value (:default input)]
       (case input-type
         :param (set! (.-value (aget audio-node input-name)) default-value)
         :constant (aset audio-node input-name default-value)
         nil)))))


(defn make-node [context position node-type]
  (let [[width height] (:default-size node-type)
        [x y] position
        node (Node. (geometry/Rectangle. x y width height)
                    node-type
                    ((:constructor node-type) context))]
    (assign-default-node-inputs node)
    (when (.-start (:audio-node node))
      (println "starting audio node")
      (.start (:audio-node node)))
    node))


(defn node-move-to [node position]
  (update-in node [:frame] #(geometry/rectangle-move-to % position)))


(defn node-slot-frames [node]
  (let [{:keys [width height]} (:frame node)
        slot-width 12
        slot-height 12

        inputs (-> node :node-type :inputs)
        input-count (count inputs)
        input-y-spacing (/ height (+ input-count 1))
        input-x (- (/ slot-width 2))

        outputs (-> node :node-type :outputs)
        output-count (count outputs)
        output-y-spacing (/ height (+ output-count 1))
        output-x (- width (/ slot-width 2))]
    (letfn [(make-frame-list [x y-spacing i [id slot]]
              [id
               [slot
                (geometry/Rectangle. x
                                     (- (* y-spacing (+ i 1))
                                        (/ slot-height 2))
                                     slot-width
                                     slot-height)]])]
      (into {}
            (concat
             (map-indexed (partial make-frame-list input-x input-y-spacing)
                          inputs)
             (map-indexed (partial make-frame-list output-x output-y-spacing)
                          outputs))))))

(defn node-slot-canvas-frames [node]
  (let [origin (-> node :frame geometry/rectangle-origin)]
    (into {}
          (map (fn [[slot-id [slot local-frame]]]
                 (let [frame (geometry/rectangle-move-by local-frame origin)]
                   [slot-id [slot frame]]))
               (node-slot-frames node)))))


(defn hit-test-slot [node position]
  (let [origin (-> node :frame geometry/rectangle-origin)]
    (when-let [matching (keep
                         (fn [[slot-id [_ frame]]]
                           (when (geometry/rectangle-hit-test frame position)
                             slot-id))
                         (node-slot-canvas-frames node))]
      (first matching))))


(defn canvas-slot-frame [node slot-id]
  (let [[_ slot-local-frame] (slot-id (node-slot-frames node))
        node-origin (-> node :frame geometry/rectangle-origin)]
    (geometry/rectangle-move-by slot-local-frame node-origin)))


(defn editable-inputs [node]
  (remove #(= (:type %) :node)
          (vals (-> node :node-type :inputs))))


(defn current-input-value [node input]
  (let [audio-node (:audio-node node)
        input-name (:name input)]
    (case (:type input)
      :param (.-value (aget audio-node input-name))
      :constant (aget audio-node input-name)
      :accessor (.call (aget audio-node input-name) audio-node)
      nil)))


(defn set-input-value [node input value]
  (let [audio-node (:audio-node node)
        input-name (:name input)]
    (case (:type input)
      :param (set! (.-value (aget audio-node input-name)) value)
      :constant (aset audio-node input-name value)
      :gate (.call (aget audio-node input-name) audio-node value)
      :accessor (.call (aget audio-node input-name) audio-node value)
      nil)))
