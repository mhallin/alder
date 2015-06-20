(ns alder.ui.components.navbar
  (:require [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]
            [taoensso.timbre :refer-macros [debug]]

            [alder.modal :as modal]))

(defn navbar-component [data owner]
  (letfn [(start-patch-name-editor [e]
            (.preventDefault e)
            (om/set-state! owner [:editing-patch-name] true))

          (stop-patch-name-editor []
            (when (-> data :node-graph :name empty?)
              (om/update! data [:node-graph :name] "Untitled patch"))
            (om/set-state! owner [:editing-patch-name] false))

          (on-name-editor-key-up [e]
            (when (= 13 (.-keyCode e))
              (.preventDefault e)
              (stop-patch-name-editor)))

          (update-patch-name [e]
            (om/update! data [:node-graph :name] (.-value (.-target e))))]
    (reify
      om/IDisplayName
      (display-name [_] "Navbar")

      om/IDidUpdate
      (did-update [_ _ prev-state]
        (let [was-editing (:editing-patch-name prev-state)
              is-editing (om/get-state owner :editing-patch-name)]
          (when (and is-editing (not was-editing))
            (let [editor (om/get-node owner "editor")]
              (.select editor)
              (debug "Editor" editor)))))

      om/IRenderState
      (render-state [_ state]
        (html
         [:div.navbar
          (let [name (-> data :node-graph :name)]
            (if (:editing-patch-name state)
              [:input.navbar__patch-name-editor
               {:value name
                :on-key-up on-name-editor-key-up
                :on-change update-patch-name
                :on-blur stop-patch-name-editor
                :ref "editor"}]
              [:h2.navbar__patch-name
               {:on-double-click start-patch-name-editor}
               name]))
          [:div.navbar__aux-button-container
           [:a.navbar__aux-button
            {:href "#"
             :on-click (partial modal/show-modal-window data :share)}
            "Share"]
           [:a.navbar__aux-button
            {:on-click (partial modal/show-modal-window data :export)
             :href "#"}
            "Export"]]])))))
