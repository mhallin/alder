(ns alder.core
  (:use ring.util.response
        ring.middleware.resource
        ring.middleware.content-type
        ring.middleware.not-modified

        compojure.core)

  (:require [alder.db :as db]
            [alder.views :as views]

            [clojure.string :as string]
            
            [compojure.route :as route]
            [compojure.handler :as handler]
            [compojure.response :as response]

            [chord.http-kit :refer [with-channel]]
            [org.httpkit.server :refer [run-server]]
            [clojure.core.async :refer [<! >! put! close! go-loop]]))

(db/migrate)

(defmulti handle-message (fn [cmd _] cmd))

(defmethod handle-message :create-new [_ _]
  (let [patch (db/create-patch!)]
    (println "Created patch" patch)
    (:short_id patch)))

(defn api-channel-handler [request]
  (println "Opened connection from" (:remote-addr request))
  (with-channel request ws-ch
    (go-loop []
      (when-let [{:keys [message]} (<! ws-ch)]
        (println "Message received:" message)
        (let [[command args] message
              response (handle-message command args)]
          (>! ws-ch response))
        (recur)))))

(defroutes main-routes
  (GET "/" [] (views/index-page))
  (GET ["/:short-id" :short-id #"[A-Za-z0-9]{12}"] [short-id] (views/index-page))
  (GET "/alder-api-ws" [] api-channel-handler))

(def app
  (-> (handler/site main-routes)
      (wrap-resource "public")
      (wrap-content-type)
      (wrap-not-modified)))

(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))

(defn -main [& args]
  (reset! server (run-server app {:port 3449})))
