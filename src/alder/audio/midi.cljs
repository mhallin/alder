(ns alder.audio.midi)

(defn midi-note->frequency [note]
  (* 440 (.pow js/Math 2 (/ (- note 69) 12))))

(defn dispatch-note-on [context audio-node note velocity]
  (when-let [frequency (.-frequency audio-node)]
    (.linearRampToValueAtTime frequency (midi-note->frequency note)
                              (.-currentTime context))
    (set! (.-value frequency) (midi-note->frequency note)))
  (when-let [gate (.-gate audio-node)]
    (.gate gate (/ velocity 128))))

(defn dispatch-note-off [context audio-node note]
  (when-let [gate (.-gate audio-node)]
    (.gate gate 0)))

(defn process-midi-message [context audio-node event]
  (when (>= (-> event .-data .-length) 3)
    (let [message (-> event .-data (aget 0) (bit-and 0xf0))
          note (-> event .-data (aget 1) (bit-and 0x7f))
          velocity (-> event .-data (aget 2) (bit-and 0x7f))]
      (cond (and (= message 0x90) (> velocity 0))
            (dispatch-note-on context audio-node note velocity)

            (or (= message 0x80) (and (= message 0x90) (= velocity 0)))
            (dispatch-note-off context audio-node note)))))

(defn make-midi-note-node [context]
  (let [audio-node #js {:_device nil
                        :gate nil
                        :frequency nil}
        on-midi-message #(process-midi-message context audio-node %)]
    (letfn [(get-set-device
              ([]
                 (.-_device audio-node))
              ([device]
                 (when-let [existing (.-_device audio-node)]
                   (set! (.-onmidimessage existing) nil))
                 (set! (.-_device audio-node) device)
                 (when device
                   (set! (.-onmidimessage device) on-midi-message))))

            (connect [destination index]
              (case index
                0 (set! (.-gate audio-node) destination)
                1 (set! (.-frequency audio-node) destination)))

            (disconnect [destination index]
              (case index
                0 (set! (.-gate audio-node) nil)
                1 (set! (.-frequency audio-node) nil)))]
      (set! (.-device audio-node) get-set-device)
      (set! (.-connect audio-node) connect)
      (set! (.-disconnect audio-node) disconnect)
      audio-node)))
