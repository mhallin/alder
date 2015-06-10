(ns alder.audio.aapi)

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
