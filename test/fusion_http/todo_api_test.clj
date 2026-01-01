(ns fusion-http.todo-api-test
  "Comprehensive JSON CRUD tests for the todo API"
  (:require [hato.client :as http]
            [cheshire.core :as json]
            [clj-simple-router.core :as router]
            [matcher-combinators.test :refer [match?]]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.string :as str]
            [fusion-http.server :as server]
            [ring.middleware.defaults :as ring-defaults]
            [ring.middleware.json :as ring-json]))

;; ============================================================================
;; Test Server Setup
;; ============================================================================

(def todo-store
  (atom {}))

(defn list-todos []
  (vec (vals @todo-store)))

(defn get-todo [id]
  (get @todo-store id))

(defn add-todo! [todo]
  (let [id (str (random-uuid))
        todo-with-id (assoc todo :id id :completed false)]
    (swap! todo-store assoc id todo-with-id)
    todo-with-id))

(defn update-todo! [id updates]
  (when (contains? @todo-store id)
    (get (swap! todo-store update id merge updates) id)))

(defn mark-complete! [id]
  (update-todo! id {:completed true}))

(defn delete-todo! [id]
  (let [existed? (contains? @todo-store id)]
    (swap! todo-store dissoc id)
    existed?))

(def routes
  (router/router
   {"GET /" (fn [_]
              {:status 200
               :body {:message "Welcome to the Todo API"
                      :version "1.0.0"}})

    "GET /todos" (fn [_]
                   {:status 200
                    :body (list-todos)})

    "GET /todos/*" (fn [{:keys [path-params]}]
                     (let [[id] path-params
                           todo (get-todo id)]
                       (if todo
                         {:status 200
                          :body todo}
                         {:status 404
                          :body {:error "Todo not found"}})))

    "POST /todos" (fn [{:keys [body]}]
                    (if (and body (:title body))
                      (let [added (add-todo! body)]
                        {:status 201
                         :body added})
                      {:status 400
                       :body {:error "Title is required"}}))

    "PUT /todos/*" (fn [{:keys [path-params body]}]
                     (let [[id] path-params
                           updated (update-todo! id body)]
                       (if updated
                         {:status 200
                          :body updated}
                         {:status 404
                          :body {:error "Todo not found"}})))

    "POST /todos/*/complete" (fn [{:keys [path-params]}]
                               (let [[id] path-params
                                     updated (mark-complete! id)]
                                 (if updated
                                   {:status 200
                                    :body updated}
                                   {:status 404
                                    :body {:error "Todo not found"}})))

    "DELETE /todos/*" (fn [{:keys [path-params]}]
                        (let [[id] path-params
                              existed? (delete-todo! id)]
                          (if existed?
                            {:status 204}
                            {:status 404
                             :body {:error "Todo not found"}})))

    "* /**" (fn [_]
              {:status 404
               :body {:error "Not Found"}})}))

(def app
  "A real-ish HTTP API using JSON"
  (-> routes
      (ring-json/wrap-json-body {:keywords? true})
      (ring-defaults/wrap-defaults ring-defaults/api-defaults)
      (ring-json/wrap-json-response)))

(defn request
  "Make an HTTP request and parse JSON response"
  [{:keys [method url body]}]
  (http/request (cond-> {:url url
                         :method method
                         :throw-exceptions false
                         :coerce :always
                         :content-type "application/json"
                         :as :json}
                  body (assoc :body (json/generate-string body)))))

(use-fixtures :each (fn [test]
                      (reset! todo-store {})
                      (test)))

(use-fixtures :once (fn [test]
                      (with-open [_server-instance (server/run-fusion-http-server app {:port 4002})]
                        (test))))

;; ============================================================================
;; CRUD Tests
;; ============================================================================

(deftest root-endpoint-test
  (testing "GET / returns welcome message"
    (let [{:keys [status body]} (request {:url "http://localhost:4002/"
                                          :method :get})]
      (is (= 200 status))
      (is (match? {:message "Welcome to the Todo API"
                   :version "1.0.0"}
                  body)))))

(deftest list-empty-todos-test
  (testing "GET /todos returns empty list initially"
    (let [{:keys [status body]} (request {:url "http://localhost:4002/todos"
                                          :method :get})]
      (is (= 200 status))
      (is (= [] body)))))

(deftest create-todo-test
  (testing "POST /todos creates a new todo"
    (let [{:keys [status body]} (request {:url "http://localhost:4002/todos"
                                          :method :post
                                          :body {:title "Buy groceries"
                                                 :description "Milk, eggs, bread"}})]
      (is (= 201 status))
      (is (match? {:id string?
                   :title "Buy groceries"
                   :description "Milk, eggs, bread"
                   :completed false}
                  body))))

  (testing "POST /todos without title returns 400"
    (let [{:keys [status body]} (request {:url "http://localhost:4002/todos"
                                          :method :post
                                          :body {:description "Missing title"}})]
      (is (= 400 status))
      (is (= {:error "Title is required"} body)))))

(deftest read-todo-test
  (testing "GET /todos/:id returns specific todo"
    ;; Create a todo first
    (let [created (:body (request {:url "http://localhost:4002/todos"
                                   :method :post
                                   :body {:title "Test todo"}}))
          todo-id (:id created)
          {:keys [status body]} (request {:url (str "http://localhost:4002/todos/" todo-id)
                                          :method :get})]
      (is (= 200 status))
      (is (= created body))))

  (testing "GET /todos/:id with non-existent ID returns 404"
    (let [{:keys [status body]} (request {:url "http://localhost:4002/todos/nonexistent"
                                          :method :get})]
      (is (= 404 status))
      (is (= {:error "Todo not found"} body)))))

(deftest update-todo-test
  (testing "PUT /todos/:id updates a todo"
    ;; Create a todo first
    (let [created (:body (request {:url "http://localhost:4002/todos"
                                   :method :post
                                   :body {:title "Original title"}}))
          todo-id (:id created)
          {:keys [status body]} (request {:url (str "http://localhost:4002/todos/" todo-id)
                                          :method :put
                                          :body {:title "Updated title"
                                                 :description "New description"}})]
      (is (= 200 status))
      (is (match? {:id todo-id
                   :title "Updated title"
                   :description "New description"
                   :completed false}
                  body))))

  (testing "PUT /todos/:id with non-existent ID returns 404"
    (let [{:keys [status body]} (request {:url "http://localhost:4002/todos/nonexistent"
                                          :method :put
                                          :body {:title "Updated"}})]
      (is (= 404 status))
      (is (= {:error "Todo not found"} body)))))

(deftest complete-todo-test
  (testing "POST /todos/:id/complete marks todo as complete"
    ;; Create a todo first
    (let [created (:body (request {:url "http://localhost:4002/todos"
                                   :method :post
                                   :body {:title "Todo to complete"}}))
          todo-id (:id created)
          {:keys [status body]} (request {:url (str "http://localhost:4002/todos/" todo-id "/complete")
                                          :method :post})]
      (is (= 200 status))
      (is (match? {:id todo-id
                   :title "Todo to complete"
                   :completed true}
                  body))))

  (testing "POST /todos/:id/complete with non-existent ID returns 404"
    (let [{:keys [status body]} (request {:url "http://localhost:4002/todos/nonexistent/complete"
                                          :method :post})]
      (is (= 404 status))
      (is (= {:error "Todo not found"} body)))))

(deftest delete-todo-test
  (testing "DELETE /todos/:id removes a todo"
    ;; Create a todo first
    (let [created (:body (request {:url "http://localhost:4002/todos"
                                   :method :post
                                   :body {:title "Todo to delete"}}))
          todo-id (:id created)
          {:keys [status body]} (request {:url (str "http://localhost:4002/todos/" todo-id)
                                          :method :delete})]
      (is (= 204 status))
      (is (nil? body))

      ;; Verify it's actually deleted
      (let [{:keys [status]} (request {:url (str "http://localhost:4002/todos/" todo-id)
                                       :method :get})]
        (is (= 404 status)))))

  (testing "DELETE /todos/:id with non-existent ID returns 404"
    (let [{:keys [status body]} (request {:url "http://localhost:4002/todos/nonexistent"
                                          :method :delete})]
      (is (= 404 status))
      (is (= {:error "Todo not found"} body)))))

(deftest full-crud-workflow-test
  (testing "Complete CRUD workflow"
    ;; 1. List empty todos
    (let [{:keys [status body]} (request {:url "http://localhost:4002/todos"
                                          :method :get})]
      (is (= 200 status))
      (is (= [] body)))

    ;; 2. Create multiple todos
    (let [todo1 (:body (request {:url "http://localhost:4002/todos"
                                 :method :post
                                 :body {:title "First todo"}}))
          todo2 (:body (request {:url "http://localhost:4002/todos"
                                 :method :post
                                 :body {:title "Second todo"}}))
          todo3 (:body (request {:url "http://localhost:4002/todos"
                                 :method :post
                                 :body {:title "Third todo"}}))]

      ;; 3. List all todos
      (let [{:keys [status body]} (request {:url "http://localhost:4002/todos"
                                            :method :get})]
        (is (= 200 status))
        (is (= 3 (count body))))

      ;; 4. Update one todo
      (request {:url (str "http://localhost:4002/todos/" (:id todo1))
                :method :put
                :body {:title "Updated first todo"
                       :description "Added description"}})

      ;; 5. Mark one todo as complete
      (request {:url (str "http://localhost:4002/todos/" (:id todo2) "/complete")
                :method :post})

      ;; 6. Delete one todo
      (request {:url (str "http://localhost:4002/todos/" (:id todo3))
                :method :delete})

      ;; 7. Verify final state
      (let [{:keys [body]} (request {:url "http://localhost:4002/todos"
                                     :method :get})]
        (is (= 2 (count body)))
        (is (match? #{{:id (:id todo1)
                       :title "Updated first todo"
                       :description "Added description"
                       :completed false}
                      {:id (:id todo2)
                       :title "Second todo"
                       :completed true}}
                    (set body)))))))
