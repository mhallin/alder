(ns alder.node
  (:require [alder.geometry :as geometry]
            [alder.node-type :as node-type]
            [taoensso.timbre :refer-macros [debug]]))

(defrecord Node
    [frame node-type-id audio-node])

(defn node-type [node]
  (-> node :node-type-id node-type/get-node-type))

(defn node-type-inputs [node]
  (-> node node-type :inputs))

(defn node-type-outputs [node]
  (-> node node-type :outputs))

(defn set-input-value [node input value]
  (let [audio-node (:audio-node node)
        input-name (:name input)]
    (case (:type input)
      :param (set! (.-value (aget audio-node input-name)) value)
      :constant (aset audio-node input-name value)
      :gate (.call (aget audio-node input-name) audio-node value)
      :accessor (.call (aget audio-node input-name) audio-node value)
      nil)
    (assoc-in node [:input-values input-name] value)))

(defn- assign-default-node-inputs [node]
  (reduce (fn [node [_ input]]
            (set-input-value node input (:default input)))
          node
          (-> node node-type :inputs)))

(defn make-node [context position node-type-id]
  (let [node-type (node-type/get-node-type node-type-id)
        [width height] (:default-size node-type)
        [x y] position
        node (Node. (geometry/Rectangle. x y width height)
                    node-type-id
                    ((:constructor node-type) context))
        node (assign-default-node-inputs node)]
    (when (.-start (:audio-node node))
      (debug "starting audio node" (:audio-node node) (.-start (:audio-node node)))
      (.start (:audio-node node) 0))
    node))

(defn node-move-to [node position]
  (update node :frame #(geometry/rectangle-move-to % position)))

(defn node-move-by [node offset]
  (update node :frame #(geometry/rectangle-move-by % offset)))

(defn node-slot-frames [node]
  (let [{:keys [width height]} (:frame node)
        slot-width 12
        slot-height 12

        inputs (-> node node-type :inputs)
        input-count (count inputs)
        input-y-spacing (/ height (inc input-count))
        input-x (- (/ slot-width 2))

        outputs (-> node node-type :outputs)
        output-count (count outputs)
        output-y-spacing (/ height (inc output-count))
        output-x (- width (/ slot-width 2))]
    (letfn [(make-frame-list [x y-spacing i [id slot]]
              [id
               [slot
                (geometry/Rectangle. x
                                     (- (* y-spacing (inc i))
                                        (/ slot-height 2))
                                     slot-width
                                     slot-height)]])]
      (into {}
            (concat
             (map-indexed (partial make-frame-list input-x input-y-spacing)
                          inputs)
             (map-indexed (partial make-frame-list output-x output-y-spacing)
                          outputs))))))

(defn node-slot-canvas-frames [node]
  (let [origin (-> node :frame geometry/rectangle-origin)]
    (into {}
          (map (fn [[slot-id [slot local-frame]]]
                 (let [frame (geometry/rectangle-move-by local-frame origin)]
                   [slot-id [slot frame]]))
               (node-slot-frames node)))))


(defn hit-test-slot [node position]
  (let [origin (-> node :frame geometry/rectangle-origin)]
    (when-let [matching (keep
                         (fn [[slot-id [_ frame]]]
                           (when (geometry/rectangle-hit-test frame position)
                             slot-id))
                         (node-slot-canvas-frames node))]
      (first matching))))


(defn canvas-slot-frame [node slot-id]
  (let [[_ slot-local-frame] (slot-id (node-slot-frames node))
        node-origin (-> node :frame geometry/rectangle-origin)]
    (geometry/rectangle-move-by slot-local-frame node-origin)))


(defn editable-inputs [node]
  (remove (fn [[_ i]] (= (:type i) :node))
          (-> node node-type :inputs)))


(defn current-input-value [node input]
  (let [audio-node (:audio-node node)
        input-name (:name input)]
    (case (:type input)
      :param (.-value (aget audio-node input-name))
      :constant (aget audio-node input-name)
      :accessor (.call (aget audio-node input-name) audio-node)
      nil)))

(defn can-connect [[from-node from-slot-id] [to-node to-slot-id]]
  (let [from-output (-> from-node node-type :outputs from-slot-id)
        from-input (-> from-node node-type :inputs from-slot-id)
        to-output (-> to-node node-type :outputs to-slot-id)
        to-input (-> to-node node-type :inputs to-slot-id)]
    (when (or (and from-output (nil? to-output)) (and from-input (nil? to-input)))
      (let [input (or from-input to-input)
            output (or from-output to-output)]
        (or (and (= (:type input) :node)
                 (= (:data-type output) :signal))
            (and (= (:type input) :param)
                 (= (:data-type output) :signal))
            (= (:type input) :null-node)
            (= (:type output) :null-node)
            (and (= (:data-type output) :param)
                 (= (:type input) :param))
            (and (= (:data-type output) :gate)
                 (= (:type input) :gate)))))))
