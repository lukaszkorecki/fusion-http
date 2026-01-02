(ns example.core
  (:gen-class)
  (:require
   [clj-simple-router.core :as router]
   [clojure.tools.logging :as log]
   cheshire.core
   [fusion-http.server :as server]
   [ring.middleware.defaults :as ring-defaults]
   [ring.middleware.json :as ring-json]))

(set! *warn-on-reflection* true)

(def state (atom 0))

(def routes
  (router/router
   {"GET /" (fn [_]
              (log/info "Received request at /")
              (swap! state inc)
              {:status 200
               :body {:message "hello"}})

    "GET /stats" (fn [_]
                   (log/info "Received request at /stats")
                   {:status 200
                    :body {:count @state}})}))

(def app
  (-> routes
      (ring-json/wrap-json-body {:keywords? true})
      (ring-defaults/wrap-defaults ring-defaults/api-defaults)
      (ring-json/wrap-json-response)))

(defn -main [& _args]

  (with-open [_server-instance (server/create app {:port 4000})]
    (log/info "Server started on port 4000")
    (loop []
      (Thread/sleep 10000)
      (recur))))
