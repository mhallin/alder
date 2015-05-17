(ns alder.audio.midi
  (:require [alder.audio.aapi :as aapi]
            [alder.audio.midiapi :as midiapi]))

(defn midi-note->frequency [note]
  (* 440 (.pow js/Math 2 (/ (- note 69) 12))))

(defn dispatch-note-on [context audio-node note velocity]
  (when-let [frequency (aget audio-node "frequency")]
    (aapi/linear-ramp-to-value-at-time frequency (midi-note->frequency note)
                                       (aapi/current-time context))
    (set! (.-value frequency) (midi-note->frequency note)))
  (when-let [gate (aget audio-node "gate")]
    (.call (aget gate "gate") gate (/ velocity 128))))

(defn dispatch-note-off [context audio-node note]
  (when-let [gate (aget audio-node "gate")]
    (.call (aget gate "gate") gate 0)))

(defn process-midi-message [context audio-node event]
  (let [data (aget event "data")]
    (when (>= (-> data .-length) 3)
      (let [message (-> data (aget 0) (bit-and 0xf0))
            note (-> data (aget 1) (bit-and 0x7f))
            velocity (-> data (aget 2) (bit-and 0x7f))]
        (cond (and (= message 0x90) (> velocity 0))
              (dispatch-note-on context audio-node note velocity)

              (or (= message 0x80) (and (= message 0x90) (= velocity 0)))
              (dispatch-note-off context audio-node note))))))

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
                   (midiapi/set-on-midi-message! existing nil))
                 (aset audio-node "_device" device)
                 (when device
                   (midiapi/set-on-midi-message! device on-midi-message))))

            (connect [destination index]
              (case index
                0 (aset audio-node "gate" destination)
                1 (aset audio-node "frequency" destination)))

            (disconnect [destination index]
              (case index
                0 (aset audio-node "gate" nil)
                1 (aset audio-node "frequency" nil)))]
      (aset audio-node "device" get-set-device)
      (aset audio-node "connect" connect)
      (aset audio-node "disconnect" disconnect)
      audio-node)))
