(ns alder.app-state
  (:require [cljs.core.async :refer [chan]]

            [alder.node-graph :refer [make-node-graph]]
            [alder.audio.aapi :refer [AudioContext]]))

(defonce app-state
  (atom
   {:context (AudioContext.)

    :node-graph (make-node-graph)
    :selection #{}

    :dragging nil
    :modal-overlay nil
    :screen-keyboard-visible true

    :current-page :none
    :current-page-args {}

    :dragging-chan (chan)

    :modal-window-chan (chan)
    :selection-chan (chan)
    :node-drag-chan (chan)
    :prototype-node-drag-chan (chan)
    :slot-drag-chan (chan)
    }))
