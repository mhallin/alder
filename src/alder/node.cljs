(ns alder.node
  (:require [alder.geometry :as geometry]
            [alder.node-type :as node-type]
            [taoensso.timbre :refer-macros [debug]]
            [schema.core :as s :include-macros true]))

(defrecord Node
    [frame node-type-id audio-node input-values inspector-visible])

(def NodeSchema
  {:frame geometry/ValidRectangle
   :node-type-id s/Keyword
   :audio-node s/Any
   :input-values {s/Str s/Any}
   :inspector-visible s/Bool})

(s/defn node-type :- node-type/ValidNodeType
  [node :- NodeSchema]
  (-> node :node-type-id node-type/get-node-type))

(s/defn node-type-inputs :- node-type/InputMap
  [node :- NodeSchema]
  (-> node node-type :inputs))

(s/defn node-type-outputs :- node-type/OutputMap
  [node :- NodeSchema]
  (-> node node-type :outputs))

(s/defn node-input :- node-type/Input
  [node :- NodeSchema input-id :- s/Keyword]
  (input-id (node-type-inputs node)))

(s/defn node-output :- node-type/Output
  [node :- NodeSchema output-id :- s/Keyword]
  (output-id (node-type-outputs node)))

(s/defn set-input-value :- NodeSchema
  [node :- NodeSchema input :- node-type/Input value :- s/Any]
  (let [audio-node (:audio-node node)
        input-name (:name input)]
    (case (:type input)
      :param (set! (.-value (aget audio-node input-name)) value)
      :constant (aset audio-node input-name value)
      :gate (.call (aget audio-node input-name) audio-node value)
      :accessor (.call (aget audio-node input-name) audio-node value)
      nil)
    (if input-name
      (assoc-in node [:input-values input-name] value)
      node)))

(s/defn assign-default-node-inputs :- NodeSchema
  [node :- NodeSchema]
  (reduce (fn [node [_ input]]
            (set-input-value node input (:default input)))
          node
          (-> node node-type :inputs)))

(s/defn make-node :- NodeSchema
  [context :- s/Any position :- geometry/Point node-type-id :- s/Keyword]
  (let [node-type (node-type/get-node-type node-type-id)
        [width height] (:default-size node-type)
        [x y] position
        node (Node. (geometry/Rectangle. x y width height)
                    node-type-id
                    ((:constructor node-type) context)
                    {}
                    false)
        node (assign-default-node-inputs node)]
    (when (.-start (:audio-node node))
      (debug "starting audio node" (:audio-node node) (.-start (:audio-node node)))
      (.start (:audio-node node) 0))
    node))

(s/defn node-move-to :- NodeSchema
  [node :- NodeSchema position :- geometry/Point]
  (update node :frame #(geometry/rectangle-move-to % position)))

(s/defn node-move-by :- NodeSchema
  [node :- NodeSchema offset :- geometry/Point]
  (update node :frame #(geometry/rectangle-move-by % offset)))

(s/defn node-slot-frames :- {s/Keyword [(s/one node-type/Slot "slot")
                                        (s/one geometry/Rectangle "frame")]}
  [node :- NodeSchema]
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

(s/defn node-slot-canvas-frames :- {s/Keyword [(s/one node-type/Slot "slot")
                                               (s/one geometry/Rectangle "frame")]}
  [node :- NodeSchema]
  (let [origin (-> node :frame geometry/rectangle-origin)]
    (into {}
          (map (fn [[slot-id [slot local-frame]]]
                 (let [frame (geometry/rectangle-move-by local-frame origin)]
                   [slot-id [slot frame]]))
               (node-slot-frames node)))))


(s/defn hit-test-slot :- (s/maybe s/Keyword)
  [node :- NodeSchema position :- geometry/Point]
  (let [origin (-> node :frame geometry/rectangle-origin)]
    (when-let [matching (keep
                         (fn [[slot-id [_ frame]]]
                           (when (geometry/rectangle-hit-test frame position)
                             slot-id))
                         (node-slot-canvas-frames node))]
      (first matching))))


(s/defn canvas-slot-frame :- geometry/Rectangle
  [node :- NodeSchema slot-id :- s/Keyword]
  (let [[_ slot-local-frame] (slot-id (node-slot-frames node))
        node-origin (-> node :frame geometry/rectangle-origin)]
    (geometry/rectangle-move-by slot-local-frame node-origin)))


(s/defn current-input-value :- s/Any
  [node :- NodeSchema input :- node-type/Input]
  (let [audio-node (:audio-node node)
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
  (let [from-output (-> from-node node-type :outputs from-slot-id)
        from-input (-> from-node node-type :inputs from-slot-id)
        to-output (-> to-node node-type :outputs to-slot-id)
        to-input (-> to-node node-type :inputs to-slot-id)]
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
