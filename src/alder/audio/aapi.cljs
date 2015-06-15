(ns alder.audio.aapi)

(def AudioContext (or (aget js/window "AudioContext")
                      (aget js/window "webkitAudioContext")))

(defn context [audio-node]
  (aget audio-node "context"))

(defn current-time [context]
  (aget context "currentTime"))

(defn cancel-scheduled-values [param time]
  (.call (aget param "cancelScheduledValues") param time))

(defn set-value-at-time [param value time]
  (.call (aget param "setValueAtTime") param value time))

(defn linear-ramp-to-value-at-time [param value time]
  (.call (aget param "linearRampToValueAtTime") param value time))


(defn fft-size [analyser-node]
  (aget analyser-node "fftSize"))

(defn set-fft-size! [analyser-node fft-size]
  (aset analyser-node "fftSize" fft-size))

(defn frequency-bin-count [analyser-node]
  (aget analyser-node "frequencyBinCount"))

(defn get-float-frequency-data [analyser-node data-array]
  (.call (aget analyser-node "getFloatFrequencyData") analyser-node data-array))

(defn get-float-time-domain-data [analyser-node data-array]
  (.call (aget analyser-node "getFloatTimeDomainData") analyser-node data-array))

(defn get-byte-time-domain-data [analyser-node data-array]
  (.call (aget analyser-node "getByteTimeDomainData") analyser-node data-array))


(defn data-transfer [file-event]
  (aget file-event "dataTransfer"))

(defn files [file-event]
  (aget file-event "files"))

(defn file-type [file]
  (aget file "type"))

(defn decode-audio-data [context buffer on-success on-error]
  (.call (aget context "decodeAudioData") context buffer on-success on-error))

(defn file-reader-result [file-reader]
  (aget file-reader "result"))

(defn set-on-load! [file-reader callback]
  (aset file-reader "onload" callback))

(defn set-on-error! [file-reader callback]
  (aset file-reader "onerror" callback))

(defn read-as-array-buffer [file-reader file]
  (.call (aget file-reader "readAsArrayBuffer") file-reader file))
