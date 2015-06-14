(ns alder.node-type
  (:require [alder.audio.midiapi :as midiapi]))

(defrecord NodeType
    [inputs outputs extra-data
     built-in
     default-title default-size
     constructor
     export-data])

(defn- signal-in [index title]
  {:type :node, :index index, :title title})

(defn- signal-out [index title]
  {:type :node, :index index, :title title, :data-type :signal})

(defn- number-param-in
  ([name title default range]
   {:type :param,
    :name name,
    :title title,
    :default default,
    :data-type :number,
    :range range})

  ([name title default]
   {:type :param,
    :name name,
    :title title,
    :default default,
    :data-type :number}))

(defn- number-constant-in
  ([name title default range]
   {:type :constant,
    :name name,
    :title title,
    :data-type :number,
    :default default,
    :range range})

  ([name title default]
   {:type :constant,
    :name name,
    :title title,
    :data-type :number,
    :default default}))

(defn- number-accessor-in [name title default]
  {:type :accessor,
   :name name,
   :title title,
   :data-type :number,
   :default default})

(defn- string-constant-in [name title default choices]
  {:type :constant,
   :name name,
   :title title,
   :data-type :string,
   :default default,
   :choices choices})

(defn- gate-in [name title]
  {:type :gate, :name name, :title title})

(defn- gate-out [index title]
  {:type :node, :index index, :title title, :data-type :gate})

(defn- param-out [index title]
  {:type :node, :index index, :title title, :data-type :param})

(defn- null-in [index title]
  {:type :null-node, :index index, :title title})

(defn- null-out [index title]
  {:type :null-node, :index index, :title title})

(defn- midi-device-accessor-in [name title]
  {:type :accessor,
   :name name,
   :default nil,
   :title title,
   :data-type :midi-device,
   :serializable false})

(defn- boolean-accessor-in [name title default]
  {:type :accessor, :name name, :title title, :data-type :boolean, :default default})

(defn- boolean-constant-in [name title default]
  {:type :constant, :name name, :title title, :data-type :boolean, :default default})

(def audio-destination-node-type
  (NodeType. {:signal (signal-in 0 "Signal")}
             {}
             nil
             true
             "Listener"
             [100 40]
             (fn [ctx] (aget ctx "destination"))
             {:ignore-export true}))

(def oscillator-node-type
  (NodeType. {:frequency (number-param-in "frequency" "Frequency" 220 [0 22050])
              :waveform (string-constant-in "type" "Waveform" "square"
                                            ["sine" "square" "sawtooth" "triangle"])}
             {:signal (signal-out 0 "Signal")}
             nil
             false
             "Osc"
             [70 40]
             (fn [ctx] (.call (aget ctx "createOscillator") ctx))
             {:constructor "context.createOscillator()"}))

(def gain-node-type
  (NodeType. {:gain (number-param-in "gain" "Gain" 1)
              :signal-in (signal-in 0 "Signal")}
             {:signal-out (signal-out 0 "Signal")}
             nil
             false
             "Gain"
             [70 40]
             (fn [ctx] (.call (aget ctx "createGain") ctx))
             {:constructor "context.createGain()"}))

(def adsr-node-type
  (NodeType. {:gate (gate-in "gate" "Gate")
              :attack (number-constant-in "attack" "Attack" 0.01)
              :decay (number-constant-in "decay" "Decay" 0.01)
              :sustain (number-constant-in "sustain" "Sustain" 0.8)
              :release (number-constant-in "release" "Release" 0.05)}
             {:envelope (param-out 0 "Envelope")}
             nil
             false
             "ADSR"
             [70 90]
             #(js/ADSRNode. %)
             {:constructor "new ADSRNode(context)"
              :dependencies {"ADSRNode" ["audio/adsr_node"
                                         (str (.-origin js/location)
                                              "/js/audio/adsr_node.js")]}}))

(def fft-analyser-node-type
  (NodeType. {:signal-in (signal-in 0 "Signal")}
             {}
             {:inspector-fields [:fft]}
             false
             "FFT"
             [70 40]
             (fn [ctx] (.call (aget ctx "createAnalyser") ctx))
             {:ignore-export true}))

(def scope-analyser-node-type
  (NodeType. {:signal-in (signal-in 0 "Signal")}
             {}
             {:inspector-fields [:waveform]}
             false
             "Scope"
             [80 40]
             (fn [ctx] (.call (aget ctx "createAnalyser") ctx))
             {:ignore-export true}))

(def output-node-type
  (NodeType. {:signal-in (null-in 0 "Signal in")}
             {}
             nil
             false
             "Output"
             [80 40]
             (fn [ctx] #js {})
             {:type :output}))

(def input-node-type
  (NodeType. {}
             {:signal-out (null-out 0 "Signal out")}
             nil
             false
             "Input"
             [80 40]
             (fn [ctx] #js {})
             {:type :input}))

(def biquad-filter-node-type
  (NodeType. {:signal-in (signal-in 0 "Signal in")
              :frequency (number-param-in "frequency" "Frequency" 350 [0 22050])
              :detune (number-param-in "detune" "Detune" 0 [-100 100])
              :Q (number-param-in "Q" "Quality" 1 [0.0001 1000])
              :gain (number-param-in "gain" "Gain" 0 [-40 40])
              :type (string-constant-in "type" "Type" "lowpass"
                                        ["lowpass" "highpass" "bandpass"
                                         "lowshelf" "highshelf" "peaking"
                                         "notch"
                                         "allpass"])}
             {:signal-out (signal-out 0 "Signal out")}
             nil
             false
             "LO Filter"
             [90 100]
             (fn [ctx] (.call (aget ctx "createBiquadFilter") ctx))
             {:constructor "context.createBiquadFilter()"}))

(def const-source-node-type
  (NodeType. {:value (number-accessor-in "value" "Value" 1)}
             {:signal (signal-out 0 "Signal out")}
             nil
             false
             "Const"
             [80 40]
             (fn [ctx] (js/ConstSourceNode. ctx))
             {:constructor "new ConstSourceNode(context)"}))

(def stereo-panner-node-type
  (NodeType. {:pan (number-param-in "pan" "Pan" 0 [-1 1])
              :signal-in (signal-in 0 "Signal in")}
             {:signal-out (signal-out 0 "Signal out")}
             nil
             false
             "Pan"
             [70 40]
             (fn [ctx] (.call (aget ctx "createStereoPanner") ctx))
             {:constructor "context.createStereoPanner()"}))

(def stereo-splitter-node-type
  (NodeType. {:signal-in (signal-in 0 "Stereo in")}
             {:left-out (signal-out 0 "Left channel")
              :right-out (signal-out 1 "Right channel")}
             nil
             false
             "Split"
             [70 40]
             (fn [ctx] (.call (aget ctx "createChannelSplitter") ctx 2))
             {:constructor "context.createChannelSplitter(2)"}))

(def stereo-merger-node-type
  (NodeType. {:left-in (signal-in 0 "Left channel")
              :right-in (signal-in 1 "Right channel")}
             {:signal-out (signal-out 0 "Stereo out")}
             nil
             false
             "Merge"
             [80 40]
             (fn [ctx] (.call (aget ctx "createChannelMerger") ctx 2))
             {:constructor "context.createChannelMerger(2)"}))

(def delay-node-type
  (NodeType. {:signal-in (signal-in 0 "Signal in")
              :delay-time (number-param-in "delayTime" "Delay time" 0 [0 5])}
             {:signal-out (signal-out 0 "Signal out")}
             nil
             false
             "Delay"
             [80 40]
             (fn [ctx] (.call (aget ctx "createDelay") ctx 5))
             {:constructor "context.createDelay(5)"}))

(def compressor-node-type
  (NodeType. {:signal-in (signal-in 0 "Signal in")
              :threshold (number-param-in "threshold" "Threshold" -24 [-100 0])
              :knee (number-param-in "knee" "Knee" 30 [0 40])
              :ratio (number-param-in "ratio" "Ratio" 12 [1 20])
              :attack (number-param-in "attack" "Attack" 0.003 [0 1])
              :release (number-param-in "release" "Release" 0.25 [0 1])}
             {:signal-out (signal-out 0 "Signal out")}
             nil
             false
             "Compressor"
             [120 100]
             (fn [ctx] (.call (aget ctx "createDynamicsCompressor") ctx))
             {:constructor "context.createDynamicsCompressor()"}))

(def audio-buffer-source-node-type
  (NodeType. {:gate (gate-in "gate" "Gate")
              :playback-rate (number-param-in "playbackRate" "Rate" 1)
              :loop (boolean-accessor-in "loop" "Loop" false)
              :loop-start (number-accessor-in "loopStart" "Loop start" 0)
              :loop-end (number-accessor-in "loopEnd" "Loop end" 0)
              :stop-on-gate-off (boolean-constant-in "stopOnGateOff" "Stop on gate off" false)}
             {:signal-out (signal-out 0 "Signal out")}
             nil
             false
             "Audio"
             [80 100]
             #(js/BufferSourceWrapperNode. %)
             {:constructor "new BufferSourceWrapperNode(context)"
              :dependencies {"BufferSourceWrapperNode"
                             ["audio/buffer_source_wrapper_node"
                              (str (.-origin js/location)
                                   "/js/audio/buffer_source_wrapper_node.js")]}}))

(def midi-note-node-type
  (NodeType. {:device (midi-device-accessor-in "device" "Device")
              :note-mode (string-constant-in "noteMode" "Mode" "retrig"
                                             ["retrig" "legato"])
              :portamento (number-constant-in "portamento" "Portamento" 0)
              :priority (string-constant-in "priority" "Priority" "last-on"
                                            ["last-on" "highest" "lowest"])}
             {:gate (gate-out 0 "Gate")
              :frequency (param-out 1 "Frequency")}
             nil
             false
             "MIDI Note"
             [100 80]
             #(js/MIDINoteNode. %)
             {:constructor "new MIDINoteNode(context)"
              :dependencies {"MIDINoteNode" ["audio/midi_note_node"
                                             (str (.-origin js/location)
                                                  "/js/audio/midi_note_node.js")]}}))

(def midi-cc-node-type
  (NodeType. {:device (midi-device-accessor-in "device" "Device")
              :channel (number-constant-in "channel" "Channel" 0 [0 127])}
             {:value (param-out 0 "Value")}
             {:inspector-fields [:midi-cc-learn]}
             false
             "MIDI CC"
             [100 40]
             #(js/MIDICCNode. %)
             {:constructor "new MIDICCNode(context)"
              :dependencies {"MIDICCNode" ["audio/midi_cc_node"
                                           (str (.-origin js/location)
                                                "/js/audio/midi_cc_node.js")]}}))

(def has-midi-support (midiapi/has-midi-access))

(def all-node-types
  (let [basic-nodes {:audio-destination audio-destination-node-type
                     :output output-node-type
                     :input input-node-type
                     :oscillator oscillator-node-type
                     :gain gain-node-type
                     :biquad-filter biquad-filter-node-type
                     :adsr adsr-node-type
                     :const-source const-source-node-type
                     :fft fft-analyser-node-type
                     :scope scope-analyser-node-type
                     :stereo-panner stereo-panner-node-type
                     :stereo-splitter stereo-splitter-node-type
                     :stereo-merger stereo-merger-node-type
                     :delay delay-node-type
                     :compressor compressor-node-type
                     :audio-buffer-source audio-buffer-source-node-type}
        midi-nodes (if has-midi-support
                     {:midi-note midi-note-node-type
                      :midi-cc midi-cc-node-type}
                     {})]
    (merge basic-nodes midi-nodes)))

(def all-node-groups
  (letfn [(lookup [node-type-id]
            [node-type-id (all-node-types node-type-id)])]

    (let [generators [:oscillator :const-source
                      :audio-buffer-source]
          filters [:biquad-filter :gain :stereo-panner
                   :stereo-splitter :stereo-merger
                   :delay :compressor]
          envelopes [:adsr]
          midi-nodes [:midi-note :midi-cc]
          analysers [:scope :fft]
          interfaces [:input :output :audio-destination]]
      (map
       (fn [m] (update m :node-types (partial map lookup)))
       (concat [{:title "Generators" :node-types generators}
                {:title "Filters" :node-types filters}
                {:title "Envelopes" :node-types envelopes}]
               (if has-midi-support
                 [{:title "MIDI" :node-types midi-nodes}]
                 [])
               [{:title "Analysers" :node-types analysers}]
               [{:title "Interfaces" :node-types interfaces}])))))

(defn get-node-type [node-type-id]
  (all-node-types node-type-id))
