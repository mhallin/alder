(ns alder.ui.components.audio-import
  (:require [om.core :as om]
            [sablono.core :as html :refer-macros [html]]
            [taoensso.timbre :refer-macros [debug]]

            [alder.audio.aapi :as aapi]
            [alder.node :as node]))

(extend-type js/FileList
  ISeqable
  (-seq [filelist]
    (map (fn [i] (aget filelist i)) (range (.-length filelist)))))

(defn- supported-file-in-event [e]
  (let [data-transfer (aapi/data-transfer e)
        files (seq (aapi/files data-transfer))]
    (when-let [file (first files)]
      (debug "File type" (aapi/file-type file))
      (when (#{"audio/x-m4a" "audio/wav"} (aapi/file-type file))
        file))))

(defn- decode-array-buffer [context buffer on-state-change on-value-change]
  (letfn [(on-decoded [audio-buffer]
            (on-state-change :file-decoded)
            (on-value-change audio-buffer))

          (on-error [e]
            (on-state-change :file-decode-error))]
    (aapi/decode-audio-data context buffer on-decoded on-error)))

(defn- decode-dropped-data [context file on-state-change on-value-change]
  (letfn [(on-load [e]
            (on-state-change :file-loaded)
            (decode-array-buffer context (aapi/file-reader-result (.-target e))
                                 on-state-change on-value-change))

          (on-error [e]
            (on-state-change :file-load-error))]
    (let [reader (js/FileReader.)]
      (aapi/set-on-load! reader on-load)
      (aapi/set-on-error! reader on-error)
      (aapi/read-as-array-buffer reader file))))

(defn audio-import-component [[node input value on-change] owner]
  (letfn [(on-file-state-change [file-state]
            (om/set-state! owner :file-state file-state))

          (on-value-change [new-value]
            (node/set-input-value node input new-value))

          (on-drag-enter [e]
            (.preventDefault e)
            (om/set-state! owner :drag-state :drag-over))

          (on-drag-over [e]
            (.preventDefault e)
            (om/set-state! owner :drag-state :drag-over))

          (on-drag-leave [e]
            (.preventDefault e)
            (om/set-state! owner :drag-state :drag-outside))

          (on-drop [e]
            (.stopPropagation e)
            (.preventDefault e)
            (when-let [file (supported-file-in-event e)]
              (om/set-state! owner :drag-state :dropped)
              (decode-dropped-data (aapi/context (node/audio-node node))
                                   file
                                   on-file-state-change
                                   on-value-change)))]
    (reify
      om/IDisplayName
      (display-name [_]
        "AudioImport")

      om/IInitState
      (init-state [_]
        {:drag-state :inactive
         :file-state (if value :has-value :no-value)})

      om/IRenderState
      (render-state [_ state]
        (html
         [:div.node-inspector__audio-import
          {:on-drag-enter on-drag-enter
           :on-drag-over on-drag-over
           :on-drag-leave on-drag-leave
           :on-drop on-drop}
          [:div.node-inspector__audio-import-drop-text "Drop file here"]
          [:div.node-inspector__audio-import-file-state
           (case (:file-state state)
             :has-value "Audio loaded"
             :file-decoded "File decoded"
             :file-decode-error "Error while decoding"
             :file-loaded "Starting decoding..."
             :file-load-error "Error while loading"
             :no-value "")]])))))
