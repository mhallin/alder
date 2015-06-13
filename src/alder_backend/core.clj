(ns alder-backend.core
  (:require [alder-backend.db :as db]
            [alder-backend.views :as views]

            [clojure.string :as string]
            [clojure.core.async :refer [<! >! put! close! go]]

            [compojure.handler :as handler]
            [compojure.core :refer [GET defroutes]]

            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]

            [chord.http-kit :refer [with-channel]]
            [org.httpkit.server :refer [run-server]]
            [environ.core :refer [env]]
            [taoensso.timbre :as timbre :refer [info]])

  (:gen-class))

(def alder-site-defaults
  (merge site-defaults
         {:session false}))

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
    [:reply :create-new {:short-id (:short_id patch)}]))

(defmethod handle-message :save-patch [_ [patch-id patch-data]]
  (if (db/save-patch! patch-id patch-data)
    [:reply :ok nil]
    [:reply :error nil]))

(defmethod handle-message :get-patch [_ patch-id]
  (let [patch (db/get-patch patch-id)]
    (db/update-visited-at! patch-id)
    (if (:read_only patch)
      (let [new-patch (db/duplicate-patch! patch-id)]
        [:reply :create-new {:short-id (:short_id new-patch)
                             :patch-data (:patch_data new-patch)}])
      [:reply :patch-data (:patch_data (db/get-patch patch-id))])))

(defn api-channel-handler [request]
  (with-channel request ws-ch
    (go
      (info "WS" (:remote-addr request) "CONN")
      (try
        (loop []
          (when-let [{:keys [message]} (<! ws-ch)]
            (let [[command args] message]
              (info "WS"
                    (:remote-addr request)
                    "MSG"
                    command)
              (let [response (handle-message command args)]
                (>! ws-ch response)))
            (recur)))
        (finally
          (info "WS" (:remote-addr request) "DISCONN"))))))

(defroutes main-routes
  (GET "/" [] (views/index-page))
  (GET ["/:short-id" :short-id #"[A-Za-z0-9]{12}"] [short-id] (views/index-page))
  (GET "/alder-api-ws" [] api-channel-handler))

(def app
  (wrap-logging (wrap-defaults main-routes
                               alder-site-defaults)))

(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))

(defn -main [& args]
  (db/migrate)
  (let [port (or (env :alder-port) 3449)
        instance (run-server app {:port port})]
    (info "Application started on port" port)
    (reset! server instance)))
