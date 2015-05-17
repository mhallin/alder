(ns alder.core
  (:use ring.util.response
        ring.middleware.resource
        ring.middleware.content-type
        ring.middleware.not-modified))

(defn handler [request]
  (when (= (:uri request) "/")
    (-> (resource-response "index.html" {:root "public"})
        (content-type "text/html; charset=utf-8"))))

(def app
  (-> handler
      (wrap-resource "public")
      (wrap-content-type)
      (wrap-not-modified)))
