(ns alder.audio.adsr
  (:require [alder.audio.aapi :as aapi]))

(defn note-on [context param value attack decay sustain]
  (let [now (aapi/current-time context)
        decay-start (+ now attack)
        sustain-start (+ now attack sustain)
        sustain-value (* value sustain)]
    (aapi/cancel-scheduled-values param now)
    (aapi/set-value-at-time param 0 now)
    (aapi/linear-ramp-to-value-at-time param value decay-start)
    (aapi/linear-ramp-to-value-at-time param sustain-value sustain-start)))

(defn note-off [context param release]
  (let [now (aapi/current-time context)
        release-end (+ now release)]
    (aapi/cancel-scheduled-values param now)
    (aapi/linear-ramp-to-value-at-time param 0 release-end)))

(defn make-adsr-node [context]
  (let [audio-node #js {:attack nil
                        :decay nil
                        :sustain nil
                        :release nil
                        :param nil
                        :context context}]
    (letfn [(connect [param]
              (aset audio-node "param" param))

            (disconnect [param]
              (aset audio-node "param" nil))

            (set-gate [value]
              (when (aget audio-node "param")
                (if (> value 0.0)
                  (note-on (aget audio-node "context")
                           (aget audio-node "param")
                           value
                           (aget audio-node "attack")
                           (aget audio-node "decay")
                           (aget audio-node "sustain"))
                  (note-off (aget audio-node "context")
                            (aget audio-node "param")
                            (aget audio-node "release")))))]
      (aset audio-node "connect" connect)
      (aset audio-node "disconnect" disconnect)
      (aset audio-node "gate" set-gate)
      audio-node)))
