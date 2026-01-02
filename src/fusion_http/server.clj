(ns fusion-http.server
  (:require [fusion-http.server.ring :as ring]
            [fusion-http.impl.logger :as logger])
  (:import [io.fusionauth.http.server
            HTTPServer
            HTTPListenerConfiguration
            HTTPHandler HTTPRequest HTTPResponse]))

(set! *warn-on-reflection* true)

;; XXX: only synchronous handlers supported for now, no async
;; but that might not matter since FusionAuth HTTP server handles threading via virtual threads

(defn ring-fn->handler [handler]
  (reify HTTPHandler
    (^void handle [_this ^HTTPRequest request ^HTTPResponse response]
      (try
        (->> (ring/->req-map request)
             handler
             (ring/->http-response response))
        (catch Exception e
          (ring/->http-response response {:status 500
                                          :body (str "Internal Server Error: " (ex-message e))
                                          :headers {"content-type" "text/plain"}}))))))

(defn ->configuration [{:keys [port]

                        :or {port 3000}}])

(defn create [ring-handler {:keys [port]
                            :or {port 3000}
                            ;; TODO: support more options, perhaps via clojure/java.data to simplify conversion
                            :as _options}]
  {:pre [(fn? ring-handler)
         (integer? port)]}
  (doto (HTTPServer.)
    (.withHandler (ring-fn->handler ring-handler))
    (.withListener (HTTPListenerConfiguration. port))
    (.withLoggerFactory (logger/factory))
    (.start)))
