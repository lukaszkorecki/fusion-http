(ns fusion-http.server
  (:require [fusion-http.server.ring :as ring])
  (:import [io.fusionauth.http.server HTTPListenerConfiguration
            HTTPServer
            HTTPHandler HTTPRequest HTTPResponse]))

(set! *warn-on-reflection* true)

;; XXX: only synchronous handlers supported for now, no async
;; but that might not matter since FusionAuth HTTP server handles threading via virtual threads

(defn ring-fn->handler [handler]
  (reify HTTPHandler
    (^void handle [_this ^HTTPRequest request ^HTTPResponse response]
      (->> (ring/->ring-map request)
           handler
           (ring/->http-response response)))))

(defn run-fusion-http-server [ring-handler {:keys [port]
                                            :or {port 3000}
                                            ;; TODO: support more options
                                            :as _options}]
  {:pre [(fn? ring-handler)
         (integer? port)]}
  (doto (HTTPServer.)
    (.withHandler (ring-fn->handler ring-handler))
    (.withListener (HTTPListenerConfiguration. port))
    (.start)))
