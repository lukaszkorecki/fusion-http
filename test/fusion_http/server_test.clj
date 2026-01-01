(ns fusion-http.server-test
  (:require
   [clj-simple-router.core :as router]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [fusion-http.server :as server]
   [hato.client :as http]
   [matcher-combinators.test :refer [match?]]
   [ring.middleware.defaults :as ring-defaults]
   [ring.middleware.json :as ring-json])
  (:import
   [java.io File]))

(def routes
  (router/router
   {"GET /query-string" (fn [_req] {:status 200 :body "ack"})
    "GET /file-download" (fn [_req]
                           {:status 200
                            :headers {"Content-Disposition" "attachment; filename=\"example.txt\""
                                      "Content-Type" "text/plain; charset=utf-8"}
                            :body (File. "test/fusion_http/files/test.txt")})

    "POST /form-params" (fn [req]
                             ;; just return something to indicate we got the request
                          {:status 200
                           :body {:received-params (:form-params req)}})
    "* /**" (fn [_]
              {:status 404
               :body {:error "Not Found"}})}))

(def request-store (atom []))

(defn with-request-capture [handler]
  (fn [request]
    (swap! request-store conj request)
    (handler request)))

(def app
  (-> routes
      with-request-capture
      (ring-json/wrap-json-body {:keywords? true})
      (ring-defaults/wrap-defaults ring-defaults/api-defaults)
      (ring-json/wrap-json-response)))

(defn request [{:keys [method url body headers query-params form-params as]
                :or {method :get as :text}}]
  (http/request (cond-> {:url url
                         :method method
                         :throw-exceptions false
                         :coerce :always
                         :as as}
                        query-params (assoc :query-params query-params)
                        form-params (assoc :form-params form-params)
                        headers (assoc :headers headers)
                        body (assoc :body body))))

(use-fixtures :once (fn [test]
                      (reset! request-store [])
                      (with-open [_server-instance (server/run-fusion-http-server app {:port 4001})]
                        (test))))

(deftest a-404-test
  (testing "unknown route returns 404"
    (let [response (request {:url "http://localhost:4001/unknown" :as :json})]
      (is (= 404 (:status response)))
      (is (= {:error "Not Found"} (get-in response [:body]))))))

(deftest with-query-string-test
  (testing "query string is preserved in request and parsed via middleware"
    (request {:url "http://localhost:4001/query-string?filter=active"
              :method :get})
    (is (match? {:query-string "filter=active"
                 :query-params {"filter" "active"}
                 :params {:filter "active"}}
                (last @request-store)))))

(deftest form-params-test
  (testing "sends form params"
    (is (match? {:status 200
                 :body {:received-params {:one "two" :three "four"}}}
                (request {:url "http://localhost:4001/form-params"
                          :method :post
                          :as :json
                          :form-params {"one" "two" "three" "four"}})))

    (is (match? {:form-params {"one" "two" "three" "four"}} (last @request-store)))))

(deftest file-download-test
  (is (= "foobar\n"
         (:body (request {:url "http://localhost:4001/file-download" :method :get :as :text #_stream})))))
