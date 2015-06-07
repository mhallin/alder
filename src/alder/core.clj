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
            [clojure.core.async :refer [<! >! put! close! go-loop]]

            [taoensso.timbre :as timbre :refer [info]]))

(db/migrate)

(defn wrap-logging [handler]
  (fn [request]
    (let [response (handler request)]
      (info "HTTP"
            (:remote-addr request)
            (str \"
                 (-> request :request-method name string/upper-case) " "
                 (:uri request)
                 \")
            (:status response))
      response)))

(defmulti handle-message (fn [cmd _] cmd))

(defmethod handle-message :create-new [_ _]
  (let [patch (db/create-patch!)]
    [:reply :create-new (:short_id patch)]))

(defmethod handle-message :save-patch [_ args]
  [:reply :ok nil])

(defn api-channel-handler [request]
  (with-channel request ws-ch
    (go-loop []
      (when-let [{:keys [message]} (<! ws-ch)]
        (let [[command args] message
              response (handle-message command args)]
          (info "MSG"
                (:remote-addr request)
                command)
          (>! ws-ch response))
        (recur)))))

(defroutes main-routes
  (GET "/" [] (views/index-page))
  (GET ["/:short-id" :short-id #"[A-Za-z0-9]{12}"] [short-id] (views/index-page))
  (GET "/alder-api-ws" [] api-channel-handler))

(def app
  (-> (handler/site main-routes)
      (wrap-logging)
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
