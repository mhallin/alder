(ns alder.node
  (:require [alder.geometry :as geometry]
            [alder.node-type :as node-type]
            [taoensso.timbre :refer-macros [debug]]
            [schema.core :as s :include-macros true]))

(defmulti frame :tag)
(defmulti set-frame (fn [m _] (:tag m)))

(defmulti inspector-visible :tag)
(defmulti set-inspector-visible (fn [m _] (:tag m)))

(defmulti stored-input-value :tag)
(defmulti set-stored-input-value (fn [m _ _] (:tag m)))

(defmulti audio-node :tag)
(defmulti node-type-id :tag)

(defmulti node-inputs :tag)
(defmulti node-outputs :tag)

(defmulti export-data :tag)
(defmulti inspector-data :tag)
(defmulti title :tag)

(def NodeSchema {:tag s/Keyword
                 s/Keyword s/Any})

(s/defn node-type :- node-type/ValidNodeType
  [node :- NodeSchema]
  (-> node node-type-id node-type/get-node-type))

(s/defn node-input :- (s/maybe node-type/Input)
  [node :- NodeSchema input-id :- s/Keyword]
  (input-id (node-inputs node)))

(s/defn node-output :- (s/maybe node-type/Output)
  [node :- NodeSchema output-id :- s/Keyword]
  (output-id (node-outputs node)))

(s/defn set-input-value :- NodeSchema
  [node :- NodeSchema input :- node-type/Input value :- s/Any]
  (let [audio-node (audio-node node)
        input-name (:name input)]
    (case (:type input)
      :param (set! (.-value (aget audio-node input-name)) value)
      :constant (aset audio-node input-name value)
      :gate (.call (aget audio-node input-name) audio-node value)
      :accessor (.call (aget audio-node input-name) audio-node value)
      nil)
    (if input-name
      (set-stored-input-value node input-name value)
      node)))

(s/defn assign-default-node-inputs :- NodeSchema
  [node :- NodeSchema]
  (reduce (fn [node [_ input]]
            (set-input-value node input (:default input)))
          node
          (node-inputs node)))

(s/defn node-move-to :- NodeSchema
  [node :- NodeSchema position :- geometry/Point]
  (set-frame node (geometry/rectangle-move-to (frame node) position)))

(s/defn node-move-by :- NodeSchema
  [node :- NodeSchema offset :- geometry/Point]
  (set-frame node (geometry/rectangle-move-by (frame node) offset)))

(s/defn node-slot-frames :- {s/Keyword [(s/one node-type/Slot "slot")
                                        (s/one geometry/Rectangle "frame")]}
  [node :- NodeSchema]
  (let [{:keys [width height]} (frame node)
        slot-width 12
        slot-height 12

        inputs (node-inputs node)
        input-count (count inputs)
        input-y-spacing (/ height (inc input-count))
        input-x (- (/ slot-width 2))

        outputs (node-outputs node)
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

(s/defn node-slot-canvas-frames :- {s/Keyword [(s/one node-type/Slot "slot")
                                               (s/one geometry/Rectangle "frame")]}
  [node :- NodeSchema]
  (let [origin (-> node frame geometry/rectangle-origin)]
    (into {}
          (map (fn [[slot-id [slot local-frame]]]
                 (let [frame (geometry/rectangle-move-by local-frame origin)]
                   [slot-id [slot frame]]))
               (node-slot-frames node)))))


(s/defn hit-test-slot :- (s/maybe s/Keyword)
  [node :- NodeSchema position :- geometry/Point]
  (let [origin (-> node frame geometry/rectangle-origin)]
    (when-let [matching (keep
                         (fn [[slot-id [_ frame]]]
                           (when (geometry/rectangle-hit-test frame position)
                             slot-id))
                         (node-slot-canvas-frames node))]
      (first matching))))


(s/defn canvas-slot-frame :- geometry/Rectangle
  [node :- NodeSchema slot-id :- s/Keyword]
  (let [[_ slot-local-frame] (slot-id (node-slot-frames node))
        node-origin (-> node frame geometry/rectangle-origin)]
    (geometry/rectangle-move-by slot-local-frame node-origin)))


(s/defn current-input-value :- s/Any
  [node :- NodeSchema input :- node-type/Input]
  (let [audio-node (audio-node node)
        input-name (:name input)]
    (case (:type input)
      :param (.-value (aget audio-node input-name))
      :constant (aget audio-node input-name)
      :accessor (.call (aget audio-node input-name) audio-node)
      nil)))

(s/defn can-connect :- s/Bool
  [[from-node from-slot-id] :- [(s/one NodeSchema "from-node")
                                (s/one s/Keyword "from-slot-id")]
   [to-node to-slot-id] :- [(s/one NodeSchema "to-node")
                            (s/one s/Keyword "to-slot-id")]]
  (let [from-output (node-output from-node from-slot-id)
        from-input (node-input from-node from-slot-id)
        to-output (node-output to-node to-slot-id)
        to-input (node-input to-node to-slot-id)]
    (if (or (and from-output (nil? to-output)) (and from-input (nil? to-input)))
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
                 (= (:type input) :gate))
            (and (= (:data-type output) :buffer)
                 (= (:data-type input) :buffer))
            (and (= (:data-type output) :function)
                 (= (:data-type input) :function))))
      false)))
