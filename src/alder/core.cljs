(ns ^:figwheel-always alder.core
    (:require [om.core :as om :include-macros true]
              [sablono.core :as html :refer-macros [html]]
              [chord.client :refer [ws-ch]]
              [cljs.core.async :refer [<! >! put! close! chan alts!]]
              [taoensso.timbre :as timbre :refer-macros [error info debug]]
              [schema.core :as s :include-macros true]

              [alder.app-state :as app-state]
              [alder.selection :as selection]
              [alder.modal :as modal]
              [alder.dragging :as dragging]
              [alder.dom-util :as dom-util]

              [alder.node :as node]
              [alder.node-type :as node-type]
              [alder.node-graph :as node-graph]
              [alder.node-graph-serialize :as node-graph-serialize]
              [alder.geometry :as geometry]
              [alder.routes :as routes]
              [alder.comm :as comm]
              [alder.persist :as persist]
              [alder.views.source-export :refer [source-export-component]]
              [alder.views.inspector :refer [inspector-component]]
              [alder.views.node :refer [node-component]]
              [alder.views.prototype-node :refer [prototype-node-component]]
              [alder.views.share :refer [share-component]]
              [alder.views.modal-container :refer [modal-container-component]]
              [alder.views.navbar :refer [navbar-component]]
              [alder.views.index :refer [index-component]])

    (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]]))

(enable-console-print!)

(def palette-width 260)

(defn- end-drag [app event]
  (dragging/mouse-up app (dom-util/event-mouse-pos event)))

(defn- update-drag [app event]
  (when (dom-util/left-button? event)
    (dragging/mouse-drag app (dom-util/event-mouse-pos event))))

(defn- start-selection-drag [app event]
  (when (dom-util/left-button? event)
    (.stopPropagation event)
    (dragging/start-selection-drag app
                                   (dom-util/event-mouse-pos event)
                                   (:selection-chan app))))

(defn- node-start-drag [app node-id event]
  (when (dom-util/left-button? event)
    (.stopPropagation event)

    (let [mouse-pos (dom-util/event-mouse-pos event)
          elem-pos (geometry/rectangle-origin
                    (dom-util/element-viewport-frame
                     (.-currentTarget event)))
          offset (geometry/point-sub mouse-pos elem-pos)]
      (dragging/start-node-drag app node-id offset (:node-drag-chan app)))))

(defn- prototype-node-start-drag [app node-type-id event]
  (when (dom-util/left-button? event)
    (.stopPropagation event)

    (let [mouse-pos (dom-util/event-mouse-pos event)
          elem-pos (geometry/rectangle-origin
                    (dom-util/element-viewport-frame
                     (.-currentTarget event)))
          offset (geometry/point-sub mouse-pos elem-pos)
          node (node/make-node (:context app)
                               (geometry/point-sub mouse-pos offset)
                               node-type-id)]
      (dragging/start-prototype-node-drag app node offset (:prototype-node-drag-chan app)))))

(defn- slot-start-drag [app node-id slot-id event]
  (.stopPropagation event)

  (let [mouse-pos (dom-util/event-mouse-pos event)
        is-input (-> @app
                     :node-graph
                     (node-graph/node-by-id node-id)
                     node/node-type-inputs
                     (contains? slot-id))]
    (dragging/start-slot-drag app node-id slot-id mouse-pos is-input (:slot-drag-chan app))))

(defn- slot-disconnect-and-start-drag [app
                                       from-node-id from-slot-id
                                       to-node-id to-slot-id event]
  (.stopPropagation event)

  (let [from [from-node-id from-slot-id]
        to [to-node-id to-slot-id]]
    (go
      (>! (:slot-drag-chan app) [:disconnect [from to]])))

  (slot-start-drag app from-node-id from-slot-id event))

(defn- connection-line [[from-x from-y] [to-x to-y] on-mouse-down]
  (let [diff-x (- to-x from-x)
        diff-y (- to-y from-y)]
    (html
     [:path.graph-canvas__connection
      {:d (str "M " (+ 1 from-x) "," (+ 1 from-y) " "
               "c "
               (/ diff-x 2) ",0 "
               (/ diff-x 2) "," diff-y " "
               diff-x "," diff-y)
       :on-mouse-down #(on-mouse-down %)}])))


(defn- connection-view [[app
                         [from-node-id from-node from-slot-id]
                         [to-node-id to-node to-slot-id]]
                        owner]
  (reify
    om/IDisplayName
    (display-name [_] "Connection")

    om/IRender
    (render [_]
      (let [[_ from-slot-frame] (-> from-node
                                    node/node-slot-canvas-frames
                                    from-slot-id)

            [_ to-slot-frame] (-> to-node
                                  node/node-slot-canvas-frames
                                  to-slot-id)]
        (connection-line (geometry/rectangle-center from-slot-frame)
                         (geometry/rectangle-center to-slot-frame)
                         #(slot-disconnect-and-start-drag app
                                                          from-node-id
                                                          from-slot-id
                                                          to-node-id
                                                          to-slot-id
                                                          %))))))

(defn- temporary-connection-view [[from-coord to-coord] owner]
  (reify
    om/IDisplayName
    (display-name [_] "TemporaryConnection")

    om/IRender
    (render [_]
      (connection-line from-coord
                       to-coord
                       nil))))

(defn- current-dragging-slot [state slot-path]
  (when-let [[node-id slot-id] slot-path]
    (let [node (-> state :node-graph :nodes node-id)]
      [node slot-id])))

(defn- graph-canvas-view [data owner]
  (reify
    om/IDisplayName
    (display-name [_] "GraphCanvas")

    om/IInitState
    (init-state [_]
      {:internal-chan (chan)})

    om/IWillMount
    (will-mount [_]
      (go-loop []
        (let [selection-chan (:selection-chan data)
              node-drag-chan (:node-drag-chan data)
              internal-chan (om/get-state owner :internal-chan)
              slot-drag-chan (:slot-drag-chan data)
              [msg ch] (alts! [internal-chan
                               selection-chan
                               node-drag-chan
                               slot-drag-chan]
                              :priority true)]
          (when (= ch selection-chan)
            (when-let [[event value] msg]
              (case event
                :clear (om/update! data :selection #{})
                :update (om/update! data :selection value)
                :set-rect (om/set-state! owner :selection-rect value)
                :clear-rect (om/set-state! owner :selection-rect nil))
              (recur)))

          (when (= ch node-drag-chan)
            (let [[event value] msg
                  selection (:selection data)]
              (case event
                :offset
                (om/transact! data
                              #(update-in % [:node-graph]
                                          (fn [node-graph]
                                            (node-graph/nodes-move-by node-graph
                                                                      (:selection %1)
                                                                      value))))))
            (recur))

          (when (= ch slot-drag-chan)
            (let [[event value] msg]
              (case event
                :update (om/set-state! owner :dragging-slot value)
                :clear (om/set-state! owner :dragging-slot nil)
                :connect
                (let [[from to] value]
                  (om/transact! data
                                #(update-in % [:node-graph]
                                            (fn [node-graph]
                                              (node-graph/connect-nodes node-graph
                                                                        from to)))))
                :disconnect
                (let [[from to] value]
                  (om/transact! data
                                #(update-in % [:node-graph]
                                            (fn [node-graph]
                                              (node-graph/disconnect-nodes node-graph
                                                                           from to))))))
              (recur))))))

    om/IWillUnmount
    (will-unmount [_]
      (let [internal-chan (om/get-state owner :internal-chan)]
        (go
          (>! internal-chan :terminate)
          (close! internal-chan))))

    om/IRenderState
    (render-state [_ state]
      (html
       [:div.graph-canvas
        {:on-mouse-down (partial start-selection-drag data)}
        (when-let [selection-rect (:selection-rect state)]
          [:div.graph-canvas__selection
           {:style (geometry/rectangle->css selection-rect)}])
        (map (fn [[id n]]
               (om/build node-component
                         [id n
                          (current-dragging-slot data
                                                 (-> state :dragging-slot :slot-path))
                          (:selection data)]
                         {:opts {:on-mouse-down (fn [node-id e]
                                                  (selection/update-selection! data node-id)
                                                  (node-start-drag data node-id e))
                                 :on-slot-mouse-down (partial slot-start-drag data)}
                          :react-key id}))
             (:nodes (:node-graph data)))
        (map (fn [[node-id node]]
               (om/build inspector-component
                         [(:node-graph data)
                          node-id node]
                         {:react-key (name node-id)}))
             (->> data :node-graph :nodes
                  (filter (fn [[_ node]] (node :inspector-visible)))))
        [:svg.graph-canvas__connections
         (om/build-all connection-view
                       (map (fn [[[from-node-id from-slot-id] [to-node-id to-slot-id]]]
                              (let [from-node (-> data :node-graph :nodes from-node-id)
                                    to-node (-> data :node-graph :nodes to-node-id)]
                                [data
                                 [from-node-id from-node from-slot-id]
                                 [to-node-id to-node to-slot-id]]))
                            (-> data :node-graph :connections)))
         (when-let [{:keys [target-pos slot-path]} (:dragging-slot state)]
           (let [[node-id slot-id] slot-path
                 node (-> data :node-graph :nodes node-id)
                 [slot slot-frame] (-> node node/node-slot-canvas-frames slot-id)
                 slot-center (geometry/rectangle-center slot-frame)]
             (om/build temporary-connection-view [slot-center target-pos])))]]))))

(defn- palette-view [data owner]
  (letfn [(render-palette-group [{:keys [title node-types]}]
            (html
             [:div.palette__node-group
              [:h3.palette__node-group-title
               title]
              (om/build-all prototype-node-component
                            node-types
                            {:opts {:on-mouse-down (partial prototype-node-start-drag data)}
                             :key :default-title})]))]
    (reify
      om/IDisplayName
      (display-name [_] "Palette")

      om/IRender
      (render [_]
        (html [:div.palette
               [:div.palette__inner
                (map render-palette-group node-type/all-node-groups)
                ]])))))

(defmulti handle-dragging-sequence (fn [_ _ msg] (:type msg)))

(defmethod handle-dragging-sequence :selection [app read-chan {:keys [mouse-pos reply-chan]}]
  (let [done (chan)
        start-mouse-pos mouse-pos]
    (go
      (>! reply-chan [:set-rect (geometry/corners->rectangle mouse-pos mouse-pos)])
      (>! reply-chan [:update #{}])

      (loop []
        (let [{:keys [phase mouse-pos]} (<! read-chan)
              rect (geometry/corners->rectangle start-mouse-pos mouse-pos)
              nodes (set (map first (node-graph/nodes-in-rect (:node-graph app) rect)))]
          (>! reply-chan [:update nodes])

          (when (= phase :drag)
            (>! reply-chan [:set-rect rect])
            (recur))))

      (>! reply-chan [:clear-rect nil])
      (>! done :done))

    done))

(defmethod handle-dragging-sequence :node [app read-chan
                                           {:keys [ref-node-id offset reply-chan]}]
  (let [done (chan)]
    (go
      (persist/set-ignore-state-changes! true)
      (loop [last-pos (node-graph/node-position (:node-graph app) ref-node-id)]
        (let [{:keys [phase mouse-pos]} (<! read-chan)
              new-node-pos (geometry/point-sub mouse-pos offset)
              delta (geometry/point-sub new-node-pos last-pos)]
          (>! reply-chan [:offset delta])

          (when (= phase :drag)
            (recur new-node-pos))))

      (persist/set-ignore-state-changes! false)
      (>! done :done))
    done))

(defmethod handle-dragging-sequence :prototype-node [app read-chan
                                                     {:keys [node offset reply-chan]}]
  (let [done (chan)]
    (go
      (>! reply-chan [:update node])

      (loop [node node]
        (let [{:keys [phase mouse-pos]} (<! read-chan)
              last-pos (geometry/rectangle-origin (:frame node))
              new-node-pos (geometry/point-sub mouse-pos offset)
              delta (geometry/point-sub new-node-pos last-pos)
              node (node/node-move-by node delta)]
          (>! reply-chan [:update node])

          (when (= phase :drag)
            (recur node))

          (when (and (= phase :end)
                     (< (first mouse-pos)
                        (- (.-innerWidth js/window) palette-width)))
            (>! reply-chan [:commit node]))))

      (>! reply-chan [:clear nil])
      (>! done :done))
    done))

(defmethod handle-dragging-sequence :slot-drag [app read-chan
                                                {:keys [slot-path
                                                        reverse reply-chan
                                                        position]}]
  (let [done (chan)]
    (go
      (>! reply-chan [:update {:slot-path slot-path
                               :target-pos position}])

      (loop []
        (let [{:keys [phase mouse-pos]} (<! read-chan)]
          (>! reply-chan [:update {:slot-path slot-path
                                   :target-pos mouse-pos}])
          (when (= phase :drag)
            (recur))

          (when (= phase :end)
            (when-let [hit (-> app :node-graph
                               (node-graph/hit-test-slot mouse-pos))]
              (let [from (if reverse hit slot-path)
                    to (if reverse slot-path hit)]
                (>! reply-chan [:connect [from to]]))))))

      (>! reply-chan [:clear nil])
      (>! done :done))
    done))

(defn- new-node-component [data owner]
  (reify
    om/IDisplayName
    (display-name [_] "NewNode")

    om/IInitState
    (init-state [_]
      {:internal-chan (chan)})

    om/IWillMount
    (will-mount [_]
      (go
        (loop []
          (let [prototype-node-drag-chan (:prototype-node-drag-chan data)
                internal-chan (om/get-state owner :internal-chan)
                [msg ch] (alts! [internal-chan prototype-node-drag-chan]
                                :priority true)]
            (when (= ch prototype-node-drag-chan)
              (let [[event value] msg]
                (case event
                  :update (om/set-state! owner :new-node value)
                  :clear (om/set-state! owner :new-node nil)
                  :commit
                  (om/transact! data
                                (fn [app]
                                  (let [node-graph (:node-graph app)
                                        node-id (node-graph/next-node-id node-graph)]
                                    (-> app
                                        (update-in [:node-graph]
                                                   #(node-graph/add-node node-graph
                                                                         node-id
                                                                         value))
                                        (assoc-in [:selection] #{node-id}))))))
                (recur)))))))

    om/IWillUnmount
    (will-unmount [_]
      (let [internal-chan (om/get-state owner :internal-chan)]
        (go
          (>! internal-chan :terminate)
          (close! internal-chan))))

    om/IRenderState
    (render-state [_ state]
      (html
       (when-let [new-node (:new-node state)]
         (om/build node-component [nil new-node nil #{}]))))))

(defn- editor-component [data owner]
  (letfn [(handle-key-up [e]
            (when (and (= (.-keyCode e) 46))
              (debug "Removing" (:selection data))
              (.preventDefault e)
              (om/transact! data
                            #(update-in % [:node-graph]
                                        (fn [node-graph]
                                          (node-graph/remove-nodes node-graph
                                                                   (:selection %)))))
              (selection/clear-selection! data)))]
    (reify
      om/IDisplayName
      (display-name [_] "Editor")

      om/IInitState
      (init-state [_]
        {:internal-chan (chan)})

      om/IWillMount
      (will-mount [_]
        (.addEventListener js/document
                           "keyup"
                           handle-key-up)
        (go
          (loop []
            (let [dragging-chan (:dragging-chan data)
                  internal-chan (om/get-state owner :internal-chan)
                  [msg ch] (alts! [internal-chan
                                   dragging-chan]
                                  :priority true)]
              (when (= ch dragging-chan)
                (when (= (:phase msg) :start)
                  (try
                    (<! (handle-dragging-sequence @data dragging-chan msg))
                    (catch js/Error e
                      (error "Caught Javascript error during drag sequence processing" e))))
                (recur))))))

      om/IWillUnmount
      (will-unmount [_]
        (.removeEventListener js/document
                              "keyup"
                              handle-key-up)
        (let [internal-chan (om/get-state owner :internal-chan)]
          (go
            (>! internal-chan :terminate)
            (close! internal-chan))))

      om/IRender
      (render [_]
        (html [:div.alder-root
               {:on-mouse-up (partial end-drag data)
                :on-mouse-move (partial update-drag data)}
               (om/build navbar-component data)
               (om/build graph-canvas-view data)
               (om/build palette-view data)
               (om/build new-node-component data)
               [:div.state-debug (pr-str data)]
               [:div.save-data-debug
                (.stringify js/JSON
                            (node-graph-serialize/serialize-graph (:node-graph data)))]
               (om/build modal-container-component data)])))))

(defn- root-component [data owner]
  (reify
    om/IDisplayName
    (display-name [_] "Root")

    om/IRender
    (render [_]
      (case (:current-page data)
        :index (om/build index-component data)
        :show-patch (om/build editor-component data)
        :none (html [:div])))))

(defn- dispatch-route [app page page-args]
  (debug "Dispatching route" page page-args)
  (if (= page :show-patch)
    (go
      (let [{:keys [short-id]} page-args
            chan (comm/get-serialized-graph short-id)
            response (<! chan)]
        (debug "Got response from get-patch" response)
        (when (= :patch-data (first response))
          (let [[_ serialized-graph] response
                graph-js-obj (.parse js/JSON serialized-graph)
                node-graph (node-graph-serialize/materialize-graph (:context @app)
                                                                   graph-js-obj)]
            (swap! app #(merge % {:current-page page
                                  :current-page-args page-args
                                  :node-graph node-graph}))))

        (when (= :create-new (first response))
          (let [[_ {:keys [short-id]}] response]
            (routes/replace-navigation! (routes/show-patch {:short-id short-id}))))))
    (swap! app #(merge % {:current-page page
                          :current-page-args page-args}))))

(routes/set-routing-callback! (partial dispatch-route app-state/app-state))
(routes/dispatch!)
(comm/start-socket-connection)
(persist/watch-state app-state/app-state)

(om/root root-component
         app-state/app-state
         {:target (. js/document (getElementById "app"))})

;; This ensures that Schema is always validating function
;; calls/responses when assertions are active
(assert
 (do (s/set-fn-validation! true) true))

(comment

  ;; Eval this in a REPL to disable logging for specific modules, or
  ;; change the logging configuration in any other way.
  (timbre/merge-config! {:ns-blacklist ["alder.persist"]})

  )

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
