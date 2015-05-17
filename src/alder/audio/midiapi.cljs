(ns alder.audio.midiapi)

(defn has-midi-access []
  (boolean (aget js/navigator "requestMIDIAccess")))

(defn request-midi-access []
  (.call (aget js/navigator "requestMIDIAccess") js/navigator))

(defn set-on-midi-message! [device event-handler]
  (aset device "onmidimessage" event-handler))
