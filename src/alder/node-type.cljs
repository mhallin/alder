(ns alder.node-type
  (:require [alder.audio.midiapi :as midiapi]))

(defrecord NodeType
    [inputs outputs extra-data
     built-in
     default-title default-size
     constructor
     export-data])

(def audio-destination-node-type
  (NodeType. {:signal {:type :node
                       :index 0
                       :title "Signal"}}
             {}
             nil
             true
             "Listener"
             [100 40]
             (fn [ctx] (aget ctx "destination"))
             {:ignore-export true}))

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
                       :title "Signal"
                       :data-type :signal}}
             nil
             false
             "Osc"
             [70 40]
             (fn [ctx] (.call (aget ctx "createOscillator") ctx))
             {:constructor "context.createOscillator()"}))

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
                           :title "Signal"
                           :data-type :signal}}
             nil
             false
             "Gain"
             [70 40]
             (fn [ctx] (.call (aget ctx "createGain") ctx))
             {:constructor "context.createGain()"}))

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
                         :title "Envelope"
                         :data-type :param}}
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
  (NodeType. {:signal-in {:type :node
                          :index 0
                          :title "Signal"}}
             {}
             {:inspector-fields [:fft]}
             false
             "FFT"
             [70 40]
             (fn [ctx] (.call (aget ctx "createAnalyser") ctx))
             {:ignore-export true}))

(def scope-analyser-node-type
  (NodeType. {:signal-in {:type :node
                          :index 0
                          :title "Signal"}}
             {}
             {:inspector-fields [:waveform]}
             false
             "Scope"
             [80 40]
             (fn [ctx] (.call (aget ctx "createAnalyser") ctx))
             {:ignore-export true}))

(def output-node-type
  (NodeType. {:signal-in {:type :null-node
                          :index 0
                          :title "Signal in"}}
             {}
             nil
             false
             "Output"
             [80 40]
             (fn [ctx] #js {})
             {:type :output}))

(def input-node-type
  (NodeType. {}
             {:signal-out {:type :null-node
                           :index 0
                           :title "Signal out"}}
             nil
             false
             "Input"
             [80 40]
             (fn [ctx] #js {})
             {:type :input}))

(def biquad-filter-node-type
  (NodeType. {:signal-in {:type :node
                          :index 0
                          :title "Signal in"}
              :frequency {:type :param
                          :name "frequency"
                          :title "Frequency"
                          :default 350
                          :data-type :number
                          :range [0 22050]}
              :detune {:type :param
                       :name "detune"
                       :title "Detune"
                       :default 0
                       :data-type :number
                       :range [-100 100]}
              :Q {:type :param
                  :name "Q"
                  :title "Quality"
                  :default 1
                  :data-type :number
                  :range [0.0001 1000]}
              :gain {:type :param
                     :name "gain"
                     :title "Gain"
                     :default 0
                     :data-type :number
                     :range [-40 40]}
              :type {:type :constant
                     :name "type"
                     :title "Type"
                     :default "lowpass"
                     :data-type :string
                     :choices ["lowpass" "highpass" "bandpass"
                               "lowshelf" "highshelf" "peaking"
                               "notch"
                               "allpass"]}}
             {:signal-out {:type :node
                           :index 0
                           :title "Signal out"
                           :data-type :signal}}
             nil
             false
             "LO Filter"
             [90 100]
             (fn [ctx] (.call (aget ctx "createBiquadFilter") ctx))
             {:constructor "context.createBiquadFilter()"}))

(def const-source-node-type
  (NodeType. {:value {:type :accessor
                      :name "value"
                      :default 1
                      :title "Value"
                      :data-type :number}}
             {:signal {:type :node
                       :index 0
                       :title "Signal out"
                       :data-type :signal}}
             nil
             false
             "Const"
             [80 40]
             (fn [ctx] (js/ConstSourceNode. ctx))
             {:constructor "new ConstSourceNode(context)"}))

(def midi-note-node-type
  (NodeType. {:device {:type :accessor
                       :name "device"
                       :default nil
                       :title "Device"
                       :data-type :midi-device
                       :serializable false}
              :note-mode {:type :constant
                          :name "noteMode"
                          :default "retrig"
                          :data-type :string
                          :title "Mode"
                          :choices ["retrig" "legato"]}
              :portamento {:type :constant
                           :name "portamento"
                           :default 0
                           :data-type :number
                           :range [0 5]
                           :title "Portamento"}
              :priority {:type :constant
                         :name "priority"
                         :default "last-on"
                         :data-type :string
                         :title "Priority"
                         :choices ["last-on" "highest" "lowest"]}}
             {:gate {:type :node
                     :index 0
                     :title "Gate"
                     :data-type :gate}
              :frequency {:type :node
                          :index 1
                          :title "Frequency"
                          :data-type :param}}
             nil
             false
             "MIDI Note"
             [110 80]
             #(js/MIDINoteNode. %)
             {:constructor "new MIDINoteNode(context)"
              :dependencies {"MIDINoteNode" ["audio/midi_note_node"
                                             (str (.-origin js/location)
                                                  "/js/audio/midi_note_node.js")]}}))

(def midi-cc-node-type
  (NodeType. {:device {:type :accessor
                       :name "device"
                       :default nil
                       :title "Device"
                       :data-type :midi-device
                       :serializable false}
              :channel {:type :constant
                        :name "channel"
                        :default 0
                        :data-type :number
                        :range [0 127]
                        :title "Channel"}}
             {:value {:type :node
                      :index 0
                      :title "Value"
                      :data-type :param}}
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
                     :scope scope-analyser-node-type}
        midi-nodes (if has-midi-support
                     {:midi-note midi-note-node-type
                      :midi-cc midi-cc-node-type}
                     {})]
    (merge basic-nodes midi-nodes)))

(def all-node-groups
  (let [generators [[:oscillator oscillator-node-type]
                    [:const-source const-source-node-type]]
        filters [[:biquad-filter biquad-filter-node-type]
                 [:gain gain-node-type]]
        envelopes [[:adsr adsr-node-type]]
        midi-nodes [[:midi-note midi-note-node-type]
                    [:midi-cc midi-cc-node-type]]
        analysers [[:oscillator oscillator-node-type]
                   [:fft fft-analyser-node-type]]
        interfaces [[:input input-node-type]
                    [:output output-node-type]
                    [:audio-destination audio-destination-node-type]]]
    (concat [{:title "Generators" :node-types generators}
             {:title "Filters" :node-types filters}
             {:title "Envelopes" :node-types envelopes}]
            (if has-midi-support
              [{:title "MIDI" :node-types midi-nodes}]
              [])
            [{:title "Analysers" :node-types analysers}]
            [{:title "Interfaces" :node-types interfaces}])))

(defn get-node-type [node-type-id]
  (all-node-types node-type-id))
