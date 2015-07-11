(ns alder.node-type
  (:require [alder.audio.midiapi :as midiapi]
            [schema.core :as s :include-macros true]))

(def SignalType
  (s/enum :boolean :number :string :midi-device :buffer :function))

(def SignalRange
  [(s/one s/Num "lower") (s/one s/Num "upper")])

(def Input
  {:type (s/enum :node :param :constant :accessor :gate :null-node),
   :title s/Str,
   (s/optional-key :name) s/Str,
   (s/optional-key :index) s/Int,
   (s/optional-key :default) s/Any,
   (s/optional-key :data-type) SignalType,
   (s/optional-key :range) SignalRange,
   (s/optional-key :choices) [s/Any],
   (s/optional-key :serializable) s/Bool})

(def InputMap
  {s/Keyword Input})

(def InputRef
  [(s/one s/Keyword "input-id") (s/one Input "input")])

(def Output
  {:type (s/enum :node :null-node),
   :title s/Str,
   :index s/Int,
   (s/optional-key :data-type) (s/enum :signal :param :gate :null-node :buffer :function)})

(def OutputMap
  {s/Keyword Output})

(def Slot
  (s/either Input Output))

(defrecord NodeType
    [inputs outputs extra-data
     built-in
     default-title default-size
     constructor
     export-data])

(def ValidNodeType
  {:inputs InputMap,
   :outputs OutputMap,
   :extra-data s/Any,
   :built-in s/Bool,
   :default-title s/Str,
   :default-size [(s/one s/Int "w") (s/one s/Int "h")],
   :constructor s/Any,
   :export-data s/Any})

(s/defn signal-in :- Input
  [index :- s/Int title :- s/Str]
  {:type :node, :index index, :title title})

(s/defn signal-out :- Output
  [index :- s/Int title :- s/Str]
  {:type :node, :index index, :title title, :data-type :signal})

(s/defn number-param-in :- Input
  ([name :- s/Str title :- s/Str default :- s/Num range :- SignalRange]
   {:type :param,
    :name name,
    :title title,
    :default default,
    :data-type :number,
    :range range})

  ([name :- s/Str title :- s/Str default :- s/Num]
   {:type :param,
    :name name,
    :title title,
    :default default,
    :data-type :number}))

(s/defn number-constant-in :- Input
  ([name :- s/Str title :- s/Str default :- s/Num range :- SignalRange]
   {:type :constant,
    :name name,
    :title title,
    :data-type :number,
    :default default,
    :range range})

  ([name :- s/Str title :- s/Str default :- s/Num]
   {:type :constant,
    :name name,
    :title title,
    :data-type :number,
    :default default}))

(s/defn number-accessor-in :- Input
  [name :- s/Str title :- s/Str default :- s/Num]
  {:type :accessor,
   :name name,
   :title title,
   :data-type :number,
   :default default})

(s/defn string-constant-in :- Input
  [name :- s/Str title :- s/Str default :- s/Str choices :- [s/Str]]
  {:type :constant,
   :name name,
   :title title,
   :data-type :string,
   :default default,
   :choices choices})

(s/defn string-accessor-in :- Input
  [name :- s/Str title :- s/Str default :- (s/maybe s/Str)]
  {:type :accessor,
   :name name,
   :title title,
   :data-type :string,
   :default default})

(s/defn gate-in :- Input
  [name :- s/Str title :- s/Str]
  {:type :gate, :name name, :title title})

(s/defn gate-out :- Output
  [index :- s/Int title :- s/Str]
  {:type :node, :index index, :title title, :data-type :gate})

(s/defn param-out :- Output
  [index :- s/Int title :- s/Str]
  {:type :node, :index index, :title title, :data-type :param})

(s/defn null-in :- Input
  [index :- s/Int title :- s/Str]
  {:type :null-node, :index index, :title title})

(s/defn null-out :- Output
  [index :- s/Int title :- s/Str]
  {:type :null-node, :index index, :title title})

(s/defn midi-device-accessor-in :- Input
  [name :- s/Str title :- s/Str]
  {:type :accessor,
   :name name,
   :default (midiapi/midi-master-device),
   :title title,
   :data-type :midi-device,
   :serializable false})

(s/defn boolean-accessor-in :- Input
  [name :- s/Str title :- s/Str default :- s/Bool]
  {:type :accessor, :name name, :title title, :data-type :boolean, :default default})

(s/defn boolean-constant-in :- Input
  [name :- s/Str title :- s/Str default :- s/Bool]
  {:type :constant, :name name, :title title, :data-type :boolean, :default default})

(s/defn buffer-constant-in :- Input
  [name :- s/Str title :- s/Str]
  {:type :constant, :name name, :title title, :data-type :buffer, :serializable false})

(s/defn buffer-out :- Output
  [index :- s/Int title :- s/Str]
  {:type :node, :title title, :index index, :data-type :buffer})

(s/defn function-accessor-in :- Input
  [name :- s/Str title :- s/Str]
  {:type :accessor, :name name, :title title, :data-type :function, :default nil,
   :serializable false})

(s/defn function-out :- Output
  [index :- s/Int title :- s/Str]
  {:type :node, :title title, :index index, :data-type :function})

(s/def audio-destination-node-type :- ValidNodeType
  (NodeType. {:signal (signal-in 0 "Signal")}
             {}
             nil
             true
             "Listener"
             [100 40]
             (fn [ctx] (aget ctx "destination"))
             {:ignore-export true}))

(s/def oscillator-node-type :- ValidNodeType
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

(s/def gain-node-type :- ValidNodeType
  (NodeType. {:gain (number-param-in "gain" "Gain" 1)
              :signal-in (signal-in 0 "Signal")}
             {:signal-out (signal-out 0 "Signal")}
             nil
             false
             "Gain"
             [70 40]
             (fn [ctx] (.call (aget ctx "createGain") ctx))
             {:constructor "context.createGain()"}))

(s/def adsr-node-type :- ValidNodeType
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
             #(let [ctor (aget (midiapi/alder-ns-obj) "ADSRNode")] (ctor. %))
             {:constructor "new ADSRNode(context)"
              :dependencies {"ADSRNode" ["audio/adsr_node"
                                         (str (.-origin js/location)
                                              "/js/audio/adsr_node.js")]}}))

(s/def fft-analyser-node-type :- ValidNodeType
  (NodeType. {:signal-in (signal-in 0 "Signal")}
             {}
             {:inspector-fields [:fft]}
             false
             "FFT"
             [70 40]
             (fn [ctx] (.call (aget ctx "createAnalyser") ctx))
             {:ignore-export true}))

(s/def scope-analyser-node-type :- ValidNodeType
  (NodeType. {:signal-in (signal-in 0 "Signal")}
             {}
             {:inspector-fields [:waveform]}
             false
             "Scope"
             [80 40]
             (fn [ctx] (.call (aget ctx "createAnalyser") ctx))
             {:ignore-export true}))

(s/def output-node-type :- ValidNodeType
  (NodeType. {:signal-in (null-in 0 "Signal in")}
             {}
             nil
             false
             "Output"
             [80 40]
             (fn [ctx] #js {})
             {:type :output}))

(s/def input-node-type :- ValidNodeType
  (NodeType. {}
             {:signal-out (null-out 0 "Signal out")}
             nil
             false
             "Input"
             [80 40]
             (fn [ctx] #js {})
             {:type :input}))

(s/def biquad-filter-node-type :- ValidNodeType
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

(s/def const-source-node-type :- ValidNodeType
  (NodeType. {:value (number-accessor-in "value" "Value" 1)}
             {:signal (signal-out 0 "Signal out")}
             nil
             false
             "Const"
             [80 40]
             #(let [ctor (aget (midiapi/alder-ns-obj) "ConstSourceNode")] (ctor. %))
             {:constructor "new ConstSourceNode(context)"}))

(s/def stereo-panner-node-type :- ValidNodeType
  (NodeType. {:pan (number-param-in "pan" "Pan" 0 [-1 1])
              :signal-in (signal-in 0 "Signal in")}
             {:signal-out (signal-out 0 "Signal out")}
             nil
             false
             "Pan"
             [70 40]
             (fn [ctx] (.call (aget ctx "createStereoPanner") ctx))
             {:constructor "context.createStereoPanner()"}))

(s/def stereo-splitter-node-type :- ValidNodeType
  (NodeType. {:signal-in (signal-in 0 "Stereo in")}
             {:left-out (signal-out 0 "Left channel")
              :right-out (signal-out 1 "Right channel")}
             nil
             false
             "Split"
             [70 40]
             (fn [ctx] (.call (aget ctx "createChannelSplitter") ctx 2))
             {:constructor "context.createChannelSplitter(2)"}))

(s/def stereo-merger-node-type :- ValidNodeType
  (NodeType. {:left-in (signal-in 0 "Left channel")
              :right-in (signal-in 1 "Right channel")}
             {:signal-out (signal-out 0 "Stereo out")}
             nil
             false
             "Merge"
             [80 40]
             (fn [ctx] (.call (aget ctx "createChannelMerger") ctx 2))
             {:constructor "context.createChannelMerger(2)"}))

(s/def delay-node-type :- ValidNodeType
  (NodeType. {:signal-in (signal-in 0 "Signal in")
              :delay-time (number-param-in "delayTime" "Delay time" 0 [0 5])}
             {:signal-out (signal-out 0 "Signal out")}
             nil
             false
             "Delay"
             [80 40]
             (fn [ctx] (.call (aget ctx "createDelay") ctx 5))
             {:constructor "context.createDelay(5)"}))

(s/def compressor-node-type :- ValidNodeType
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

(s/def audio-buffer-source-node-type :- ValidNodeType
  (NodeType. {:gate (gate-in "gate" "Gate")
              :buffer (buffer-constant-in "buffer" "Buffer")
              :playback-rate (number-constant-in "playbackRate" "Rate" 1)
              :loop (boolean-constant-in "loop" "Loop" false)
              :loop-start (number-constant-in "loopStart" "Loop start" 0)
              :loop-end (number-constant-in "loopEnd" "Loop end" 0)
              :play-mode (string-constant-in "playMode" "Play mode" "retrig-restart"
                                             ["retrig-restart" "gate-off-stop"])}
             {:signal-out (signal-out 0 "Signal out")}
             nil
             false
             "Audio"
             [80 120]
             #(let [ctor (aget (midiapi/alder-ns-obj) "BufferSourceWrapperNode")] (ctor. %))
             {:constructor "new BufferSourceWrapperNode(context)"
              :dependencies {"BufferSourceWrapperNode"
                             ["audio/buffer_source_wrapper_node"
                              (str (.-origin js/location)
                                   "/js/audio/buffer_source_wrapper_node.js")]}}))

(s/def url-buffer-node-type :- ValidNodeType
  (NodeType. {:url (string-accessor-in "url" "URL" nil)}
             {:buffer (buffer-out 0 "Buffer")}
             nil
             false
             "URL"
             [70 40]
             #(let [ctor (aget (midiapi/alder-ns-obj) "URLBufferNode")] (ctor. %))
             {:constructor "new URLBufferNode(context)"
              :dependencies {"URLBufferNode" ["audio/url_buffer_node"
                                              (str (.-origin js/location)
                                                   "/js/audio/url_buffer_node.js")]}}))

(s/def convolver-node-type :- ValidNodeType
  (NodeType. {:signal-in (signal-in 0 "Signal in")
              :buffer (buffer-constant-in "buffer" "IR")
              :normalize (boolean-constant-in "normalize" "Normalize" true)}
             {:signal-out (signal-out 0 "Signal out")}
             nil
             false
             "Convolver"
             [100 60]
             (fn [ctx] (.call (aget ctx "createConvolver") ctx))
             {:constructor "context.createConvolver()"}))

(s/def user-media-node-type :- ValidNodeType
  (NodeType. {:gate (gate-in "gate" "Gate")}
             {:signal (signal-out 0 "Signal")}
             nil
             false
             "Mic"
             [80 40]
             #(let [ctor (aget (midiapi/alder-ns-obj) "UserMediaNode")] (ctor. %))
             {:constructor "new UserMediaNode(context)"
              :dependencies {"UserMediaNode"
                             ["audio/user_media_node",
                              (str (.-origin js/location)
                                   "/js/audio/user_media_node.js")]}}))

(s/def programmable-node-type :- ValidNodeType
  (NodeType. {:render-callback (function-accessor-in "renderCallback" "Callback")}
             {:signal-out (signal-out 0 "Signal out")}
             {:inspector-hide-fields #{:render-callback}}
             false
             "Exec"
             [70 40]
             #(let [ctor (aget (midiapi/alder-ns-obj) "ProgrammableNode")] (ctor. %))
             {:constructor "new ProgrammableNode(context)"
              :dependencies {"ProgrammableNode"
                             ["audio/programmable_node",
                              (str (.-origin js/location)
                                   "/js/audio/programmable_node.js")]}}))

(def default-js-source
  "'use strict';

function Node(context) {
  this.context = context;
}

Node.prototype.onaudioprocess = function (event) {
  var inputBuf = event.inputBuffer;
  var inputChan = inputBuf.getChannelData(0);
  var outputBuf = event.outputBuffer;
  var outputChan = outputBuf.getChannelData(0);

  for (var i = 0; i < outputBuf.length; ++i) {
  }
}

module.exports = Node;
")

(s/def js-source-node-type :- ValidNodeType
  (NodeType. {:source (string-accessor-in "source" "Source" default-js-source)}
             {:function (function-out 0 "Function")}
             {:inspector-fields [:js-editor]
              :inspector-hide-fields #{:source}
              :inspector-width 420}
             false
             "JavaScript"
             [110 40]
             #(let [ctor (aget (midiapi/alder-ns-obj) "JSSourceNode")] (ctor. %))
             {:constructor "new JSSourceNode(context)"
              :dependencies {"JSSourceNode"
                             ["audio/js_source_node"
                              (str (.-origin js/location)
                                   "/js/audio/js_source_node.js")]}}))

(s/def midi-note-node-type :- ValidNodeType
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
             #(let [ctor (aget (midiapi/alder-ns-obj) "MIDINoteNode")] (ctor. %))
             {:constructor "new MIDINoteNode(context)"
              :dependencies {"MIDINoteNode" ["audio/midi_note_node"
                                             (str (.-origin js/location)
                                                  "/js/audio/midi_note_node.js")]}}))

(s/def midi-cc-node-type :- ValidNodeType
  (NodeType. {:device (midi-device-accessor-in "device" "Device")
              :channel (number-constant-in "channel" "Channel" 0 [0 127])}
             {:value (param-out 0 "Value")}
             {:inspector-fields [:midi-cc-learn]}
             false
             "MIDI CC"
             [100 40]
             #(let [ctor (aget (midiapi/alder-ns-obj) "MIDICCNode")] (ctor. %))
             {:constructor "new MIDICCNode(context)"
              :dependencies {"MIDICCNode" ["audio/midi_cc_node"
                                           (str (.-origin js/location)
                                                "/js/audio/midi_cc_node.js")]}}))

(s/def all-node-types :- {s/Keyword ValidNodeType}
  {:audio-destination audio-destination-node-type
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
   :audio-buffer-source audio-buffer-source-node-type
   :url-buffer url-buffer-node-type
   :convolver convolver-node-type
   :user-media user-media-node-type
   :programmable programmable-node-type
   :js-source js-source-node-type
   :midi-note midi-note-node-type
   :midi-cc midi-cc-node-type})

(s/def all-node-groups :- [{:title s/Str
                            :node-types [[(s/one s/Keyword "key")
                                          (s/one ValidNodeType "type")]]}]
  (letfn [(lookup [node-type-id]
            [node-type-id (all-node-types node-type-id)])]

    (let [generators [:oscillator :const-source
                      :audio-buffer-source
                      :user-media]
          custom-nodes [:programmable :js-source]
          filters [:biquad-filter :gain :stereo-panner
                   :stereo-splitter :stereo-merger
                   :delay :compressor :convolver]
          envelopes [:adsr]
          buffer-sources [:url-buffer]
          midi-nodes [:midi-note :midi-cc]
          analysers [:scope :fft]
          interfaces [:input :output :audio-destination]]
      (map
       (fn [m] (update m :node-types (partial map lookup)))
       [{:title "Generators" :node-types generators}
        {:title "Custom" :node-types custom-nodes}
        {:title "Filters" :node-types filters}
        {:title "Envelopes" :node-types envelopes}
        {:title "Buffer Sources" :node-types buffer-sources}
        {:title "MIDI" :node-types midi-nodes}
        {:title "Analysers" :node-types analysers}
        {:title "Interfaces" :node-types interfaces}]))))

(s/defn get-node-type :- ValidNodeType
  [node-type-id :- s/Keyword]
  (all-node-types node-type-id))
