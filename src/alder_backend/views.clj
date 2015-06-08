(ns alder-backend.views
  (:use [hiccup core page]))

(defn index-page []
  (html5
   [:head
    [:meta {:charset "utf-8"}]
    [:link {:href "//cdnjs.cloudflare.com/ajax/libs/normalize/3.0.3/normalize.min.css"
            :rel "stylesheet"
            :type "text/css"}]
    [:link {:href "css_compiled/style.css"
            :rel "stylesheet"
            :type "text/css"}]]

   [:body
    [:div#app "Loading Alder…"]
    [:script {:src "js_compiled/audio_all.js"
              :type "text/javascript"}]
    [:script {:src "cljs_compiled/alder.js"
              :type "text/javascript"}]]))
