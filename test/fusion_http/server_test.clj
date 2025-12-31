(ns fusion-http.server-test
  (:require [hato.client :as http]
            [cheshire.core :as json]
            [clj-simple-router.core :as router]
            [matcher-combinators.test :refer [match?]]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [fusion-http.server :as server]
            [ring.middleware.defaults :as ring-defaults]
            [ring.middleware.json :as ring-json]))

(def todo-store
  (atom {}))

(defn list-todos []
  (vals @todo-store))

(defn add-todo! [todo]
  (let [id (str (random-uuid))
        todo-with-id (assoc todo :id id)]
    (swap! todo-store assoc id todo-with-id)
    todo-with-id))

(defn mark-complete! [id]
  (get (swap! todo-store update id assoc :completed true) id))

(defn delete-todo! [id]
  (swap! todo-store dissoc id))

(def routes

  (router/router
   {"GET /" (fn [_]
              {:status 200
               :body {:msg "Welcome to the Todo API"}})

    "GET /todos" (fn [_]
                   {:status 200
                    :body (list-todos)})

    "POST /todos" (fn [{:keys [body]}]
                    (let [added (add-todo! body)]
                      {:status 201
                       :body added}))

    "POST /todos/:id/complete" (fn [{:keys [params]}]
                                 (let [id (:id params)
                                       updated (mark-complete! id)]
                                   (if updated
                                     {:status 200
                                      :body updated}
                                     {:status 404
                                      :body {:error "Not Found"}})))
    "DELETE /todos/:id" (fn [{:keys [params]}]
                          (let [id (:id params)]
                            (delete-todo! id)
                            {:status 204}))

    "* /**" (fn [_]
              {:status 404
               :body {:error "Not Found"}})}))

(def request-store (atom []))

(defn with-request-capture [handler]
  (fn [request]
    (swap! request-store conj request)
    (handler request)))

(def app
  "An real-ish HTTP api using JSON"
  (-> routes
      with-request-capture
      (ring-json/wrap-json-body {:keywords? true})
      (ring-defaults/wrap-defaults ring-defaults/api-defaults)
      (ring-json/wrap-json-response)))

(defn request [{:keys [method url body headers]}]
  (-> (http/request (cond-> {:url url
                             :method method
                             :throw-exceptions false
                             :as :json}
                            body (assoc :body (pr-str body))
                            headers (assoc :headers headers)))

      (update :body #(json/parse-string % true))))

(use-fixtures :once (fn [test]
                      (reset! todo-store {})
                      (reset! request-store [])
                      (with-open [_server-instance (server/run-fusion-http-server app {:port 4001})]
                        (test))))

(deftest a-404-test
  (testing "unknown route returns 404"
    (let [response (request {:url "http://localhost:4001/unknown"
                             :method :get})]
      (is (= 404 (:status response)))
      (is (= {:error "Not Found"} (get-in response [:body]))))))

(deftest get-empty-test
  (testing "no todos initially"
    (let [{:keys [status body] :as response} (request {:url "http://localhost:4001/todos"
                                                       :method :get})

          captured-request (last @request-store)]

      (testing "basic request details are caught"
        (is (match? {:uri "/todos"
                     :request-method :get
                     :protocol "HTTP/1.1"
                     :scheme :http
                     :server-name "localhost"
                     :server-port 4001
                     :remote-addr "127.0.0.1"}

                    captured-request))

        (is (match? {"accept-encoding" "gzip, deflate"
                     "host" "localhost:4001"
                     "user-agent" "Java-http-client/25.0.1"
                     "connection" "Upgrade, HTTP2-Settings"}
                    (:headers captured-request))))

      (is (= 200 status))
      (is (= [] body))

      (is (= :x response))
      )))

(deftest with-query-string-test
  (testing "query string is preserved in request and parsed via middleware"
    (request {:url "http://localhost:4001/todos?filter=active"
              :method :get})
    (is (match? {:query-string "filter=active"
                 :query-params {"filter" "active"}
                 :params {:filter "active"}}
                (last @request-store)))))

#_(deftest get-list-test

    (reset! todo-store {"1" {:id "1" :title "Test Todo" :completed false}})

    (testing "returns todos"
      (let [response (request {:url "http://localhost:4001/todos"
                               :method :get})]
        (is (= 200 (:status response)))
        (is (= [{:id "1" :title "Test Todo" :completed false}]
               (get-in response [:body])))))

    (is (= :x (last @request-store))))
