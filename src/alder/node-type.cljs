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
                       :title "Signal"}}
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
                           :title "Signal"}}
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
                         :title "Envelope"}}
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
             #(js/MIDINoteNode. %)
             {:constructor "new MIDINoteNode(context)"
              :dependencies {"MIDINoteNode" ["audio/midi_note_node"
                                             (str (.-origin js/location)
                                                  "/js/audio/midi_note_node.js")]}}))

(def has-midi-support (midiapi/has-midi-access))

(def all-node-types
  (let [basic-nodes {:audio-destination audio-destination-node-type
                     :output output-node-type
                     :oscillator oscillator-node-type
                     :gain gain-node-type
                     :adsr adsr-node-type
                     :fft fft-analyser-node-type
                     :scope scope-analyser-node-type}
        midi-nodes (if has-midi-support
                     {:midi-note midi-note-node-type}
                     {})]
    (merge basic-nodes midi-nodes)))

(defn get-node-type [node-type-id]
  (all-node-types node-type-id))
