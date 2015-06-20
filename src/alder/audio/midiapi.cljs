(ns alder.audio.midiapi)

(defn has-midi-access []
  (boolean (aget js/navigator "requestMIDIAccess")))

(defn request-midi-access []
  (.call (aget js/navigator "requestMIDIAccess") js/navigator))

(defn add-midi-event-listener [device token callback]
  (.call (aget js/Alder.MIDIDispatch "addMIDIEventListener")
         js/Alder.MIDIDispatch
         device
         token
         callback))

(defn remove-midi-event-listener [device token]
  (.call (aget js/Alder.MIDIDispatch "removeMIDIEventListener")
         js/Alder.MIDIDispatch
         device
         token))

(defn node-device [node]
  (.call (aget node "device") node))

(defn event-data [event]
  (aget event "data"))
