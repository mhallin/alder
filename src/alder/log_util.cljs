(ns alder.log-util
  (:require [taoensso.encore :as enc]))

(defn granular-console-appender []
  (when (and (exists? js/console) (.-log js/console))
    (let [have-debug-logger? (.-debug js/console)
          have-info-logger? (.-info js/console)
          have-warn-logger? (.-warn js/console)
          have-error-logger? (.-error js/console)

          level->logger {:fatal (if have-error-logger? js/console.error js/console.log)
                         :error (if have-error-logger? js/console.error js/console.log)
                         :warn (if have-warn-logger? js/console.warn js/console.log)
                         :info (if have-info-logger? js/console.info js/console.log)
                         :debug (if have-debug-logger? js/console.debug js/console.log)}]
      {:enabled? true
       :async? false
       :min-level nil
       :rate-limit nil
       :output-fn :inherit
       :fn
       (fn [data]
         (let [{:keys [level output-fn vargs_]} data
               vargs (force vargs_)
               [v1 vnext] (enc/vsplit-first vargs)
               output (if (= v1 :timbre/raw)
                        (into-array vnext)
                        (output-fn data))]
           (.call (level->logger level) js/console output)))})))
