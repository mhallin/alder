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
              [alder.ui.dragging :as dragging]
              [alder.dom-util :as dom-util]

              [alder.node :as node]
              [alder.node-type :as node-type]
              [alder.node-graph :as node-graph]
              [alder.node-graph-serialize :as node-graph-serialize]
              [alder.geometry :as geometry]
              [alder.routes :as routes]
              [alder.comm :as comm]
              [alder.persist :as persist]
              [alder.ui.interaction :as interaction]
              [alder.views.palette :refer [palette-component]]
              [alder.views.new-node :refer [new-node-component]]
              [alder.views.source-export :refer [source-export-component]]
              [alder.views.inspector :refer [inspector-component]]
              [alder.views.node :refer [node-component]]
              [alder.views.share :refer [share-component]]
              [alder.views.modal-container :refer [modal-container-component]]
              [alder.views.navbar :refer [navbar-component]]
              [alder.views.index :refer [index-component]]
              [alder.views.connection :refer [connection-component
                                              temporary-connection-component]])

    (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]]))

(enable-console-print!)

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

(defn- slot-start-drag [app node-id slot-id event]
  (.stopPropagation event)

  (let [mouse-pos (dom-util/event-mouse-pos event)
        is-input (-> @app
                     :node-graph
                     (node-graph/node-by-id node-id)
                     node/node-type-inputs
                     (contains? slot-id))]
    (dragging/start-slot-drag app node-id slot-id mouse-pos is-input (:slot-drag-chan app))))


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
         (om/build-all connection-component
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
             (om/build temporary-connection-component [slot-center target-pos])))]]))))

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
                    (<! (interaction/handle-dragging-sequence @data dragging-chan msg))
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
               (om/build palette-component data)
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
