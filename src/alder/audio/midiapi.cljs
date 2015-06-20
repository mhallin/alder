(ns alder.audio.midiapi)

(defn has-midi-access []
  (boolean (aget js/navigator "requestMIDIAccess")))

(defn request-midi-access []
  (.call (aget js/navigator "requestMIDIAccess") js/navigator))

(defn add-midi-message-event-listener [device token callback]
  (.call (aget js/Alder.MIDIDispatch "addMIDIMessageEventListener")
         js/Alder.MIDIDispatch
         device
         token
         callback))

(defn remove-midi-message-event-listener [device token]
  (.call (aget js/Alder.MIDIDispatch "removeMIDIMessageEventListener")
         js/Alder.MIDIDispatch
         device
         token))

(defn midi-master-device []
  (aget js/Alder.MIDIDispatch "masterDevice"))

(defn get-current-midi-master-device []
  (.call (aget js/Alder.MIDIDispatch "currentMasterDevice")
         js/Alder.MIDIDispatch))

(defn set-current-midi-master-device! [device]
  (.call (aget js/Alder.MIDIDispatch "currentMasterDevice")
         js/Alder.MIDIDispatch
         device))

(defn node-device [node]
  (.call (aget node "device") node))

(defn event-data [event]
  (aget event "data"))
