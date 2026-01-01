(ns fusion-http.server.component
  (:require
   [fusion-http.server :as server])
  (:import
   [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)

(defn create [{:keys [handler config]}]
  (with-meta {:config config}
    {'com.stuartsierra.component/start (fn [this]
                                         (if (:_server this)
                                           this
                                           (let [deps (dissoc this :config)
                                                 wrapped-handler (fn with-deps' [request]
                                                                   (handler (assoc request :component deps)))]
                                             (assoc this :_server (server/create wrapped-handler (:config this))))))
     'com.stuartsierra.component/stop (fn [this]

                                        (if-let [srv (:_server this)]
                                          (do
                                            (AutoCloseable/.close srv)
                                            (assoc this :_server nil))
                                          this))}))
