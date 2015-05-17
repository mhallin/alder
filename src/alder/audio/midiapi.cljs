(ns alder.audio.midiapi)

(defn request-midi-access []
  (.call (aget js/navigator "requestMIDIAccess") js/navigator))

(defn set-on-midi-message! [device event-handler]
  (aset device "onmidimessage" event-handler))
