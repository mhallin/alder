(ns alder.selection
  (:require [cljs.core.async :refer [>!]]
            [taoensso.timbre :refer-macros [debug]]
            [schema.core :as s :include-macros true])

  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn clear-selection! [app]
  (go
    (>! (:selection-chan @app) [:clear nil])))

(s/defn set-selection! [app selection :- #{s/Keyword}]
  (go
    (>! (:selection-chan @app) [:update selection])))

(s/defn update-selection! [app node-id :- s/Keyword]
  (when-not ((:selection @app) node-id)
    (set-selection! app #{node-id})))
