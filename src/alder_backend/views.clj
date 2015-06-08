(ns alder-backend.views
  (:require [clojure.data.json :as json]
            [environ.core :refer [env]]
            [clojure.java.io :as io])
  (:use [hiccup core page]))

(def cdn-root
  (or (env :cdn-root) ""))

(def asset-mapping
  (if (env :alder-production)
    (into {} (map (fn [[k v]] [k (str cdn-root v)])
                  (json/read-str (slurp (io/resource "rev-manifest.json")))))
    (fn [x] (str cdn-root x))))

(defn index-page []
  (html5
   [:head
    [:meta {:charset "utf-8"}]
    [:link {:href "//cdnjs.cloudflare.com/ajax/libs/normalize/3.0.3/normalize.min.css"
            :rel "stylesheet"
            :type "text/css"}]
    [:link {:href (asset-mapping "css_compiled/style.css")
            :rel "stylesheet"
            :type "text/css"}]]

   [:body
    [:div#app "Loading Alder…"]
    [:script {:src (asset-mapping "js_compiled/audio_all.js")
              :type "text/javascript"}]
    [:script {:src (asset-mapping "cljs_compiled/alder.js")
              :type "text/javascript"}]]))
