(ns alder.audio.adsr)

(defn note-on [context param value attack decay sustain]
  (let [now (.-currentTime context)
        decay-start (+ now attack)
        sustain-start (+ now attack sustain)
        sustain-value (* value sustain)]
    (.cancelScheduledValues param now)
    (.setValueAtTime param 0 now)
    (.linearRampToValueAtTime param value decay-start)
    (.linearRampToValueAtTime param sustain-value sustain-start)))

(defn note-off [context param release]
  (let [now (.-currentTime context)
        release-end (+ now release)]
    (.cancelScheduledValues param now)
    (.linearRampToValueAtTime param 0 release-end)))

(defn make-adsr-node [context]
  (let [audio-node #js {:attack nil
                        :decay nil
                        :sustain nil
                        :release nil
                        :param nil
                        :context context}]
    (letfn [(connect [param]
              (set! (.-param audio-node) param))

            (disconnect [param]
              (set! (.-param audio-node) nil))

            (set-gate [value]
              (when (.-param audio-node)
                (if (> value 0.0)
                  (note-on (.-context audio-node)
                           (.-param audio-node)
                           value
                           (.-attack audio-node)
                           (.-decay audio-node)
                           (.-sustain audio-node))
                  (note-off (.-context audio-node)
                            (.-param audio-node)
                            (.-release audio-node)))))]
      (set! (.-connect audio-node) connect)
      (set! (.-gate audio-node) set-gate)
      (set! (.-disconnect audio-node) disconnect)
      audio-node)))
