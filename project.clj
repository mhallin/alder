(defproject alder "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-3211"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [sablono "0.3.4"]
                 [org.omcljs/om "0.8.8"]
                 [secretary "1.2.3"]

                 [ring/ring-core "1.3.2"]
                 [ring/ring-defaults "0.1.2"]
                 [javax.servlet/servlet-api "2.5"]
                 [compojure "1.3.4"]
                 [ragtime/ragtime.sql "0.3.9"]
                 [org.postgresql/postgresql "9.4-1201-jdbc41"]
                 [environ "1.0.0"]
                 [hiccup "1.0.5"]
                 [jarohen/chord "0.6.0"]
                 [yesql "0.4.2"]
                 [org.clojure/java.jdbc "0.3.7"]
                 [com.taoensso/timbre "4.0.0"]
                 [org.clojure/tools.namespace "0.2.10"]]

  :plugins [[lein-cljsbuild "1.0.5"]
            [lein-figwheel "0.3.1"]
            [lein-ring "0.9.3"]
            [ragtime/ragtime.lein "0.3.8"]
            [lein-environ "1.0.0"]]

  :source-paths ["src"]

  :clean-targets ^{:protect false} ["resources/public/cljs_compiled" "target"]

  :main alder-backend.core

  :hooks [leiningen.cljsbuild]

  :profiles
  {:dev
   {:cljsbuild {:builds [{:id "dev"
                          :source-paths ["src"]
                          
                          :figwheel { :on-jsload "alder.core/on-js-reload" }
                          
                          :compiler {:main alder.core
                                     :asset-path "cljs_compiled/out"
                                     :output-to "resources/public/cljs_compiled/alder.js"
                                     :output-dir "resources/public/cljs_compiled/out"
                                     :source-map-timestamp true }}
                         {:id "min"
                          :source-paths ["src"]
                          :compiler {:output-to "resources/public/cljs_compiled/alder.js"
                                     :main alder.core
                                     :optimizations :advanced
                                     :pretty-print false}}]}}
   
   :uberjar
   {:env {:production true}
    :omit-source :true
    :aot :all
    :cljsbuild {:builds []}}}
  
  :figwheel {
             ;; :http-server-root "public" ;; default and assumes "resources" 
             ;; :server-port 3449 ;; default
             :css-dirs ["resources/public/css_compiled"] ;; watch and update CSS

             ;; Start an nREPL server into the running figwheel process
             :nrepl-port 7888

             ;; Server Ring Handler (optional)
             ;; if you want to embed a ring handler into the figwheel http-kit
             ;; server, this is for simple ring servers, if this
             ;; doesn't work for you just run your own server :)
             :ring-handler alder-backend.core/app

             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"

             ;; if you want to disable the REPL
             ;; :repl false

             ;; to configure a different figwheel logfile path
             ;; :server-logfile "tmp/logs/figwheel-logfile.log" 
             })
