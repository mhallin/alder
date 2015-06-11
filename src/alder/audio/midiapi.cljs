(ns alder.audio.midiapi)

(defn has-midi-access []
  (boolean (aget js/navigator "requestMIDIAccess")))

(defn request-midi-access []
  (.call (aget js/navigator "requestMIDIAccess") js/navigator))

(defn add-midi-event-listener [device token callback]
  (.call (aget js/MIDIDispatch "addMIDIEventListener")
         js/MIDIDispatch
         device
         token
         callback))

(defn remove-midi-event-listener [device token]
  (.call (aget js/MIDIDispatch "removeMIDIEventListener")
         js/MIDIDispatch
         device
         token))
