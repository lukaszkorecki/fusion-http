(ns fusion-http.server.component-test
  (:require [fusion-http.server.component :as server]
            [clojure.test :refer [deftest is]]
            [com.stuartsierra.component :as component]))

(deftest component-test
  (let [port (+ 3000 (rand-int 1000))
        system (-> {:echo (fn [s] (str "echo: " s))
                    :web-server (component/using
                                 (server/create {:config {:port port}
                                                 :handler (fn [{:keys [uri component]}]
                                                            (let [echo-fn (:echo component)]
                                                              {:status 200
                                                               :body (echo-fn uri)}))})
                                 [:echo])}

                   (component/map->SystemMap)
                   (component/start))]

    (is (instance? io.fusionauth.http.server.HTTPServer (-> system :web-server :_server)))

    (is (= "echo: /test/foo" (slurp (format "http://localhost:%s/test/foo" port))))
    (component/stop system)))
