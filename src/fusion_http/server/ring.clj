(ns fusion-http.server.ring
  (:require [ring.core.protocols :as ring-protocols]
            [clojure.string :as str])
  (:import [java.net URI]
           [io.fusionauth.http.server HTTPRequest HTTPResponse]))

(set! *warn-on-reflection* true)

;; as per Ring spec:
;; A request map contains the following:

;; Key 	Type 	Required 	Deprecated
;; :body 	java.io.InputStream

;; :headers 	{String String} 	Yes
;; :protocol 	String 	Yes
;; :query-string 	String
;; :remote-addr 	String 	Yes
;; :request-method 	Keyword 	Yes
;; :scheme 	Keyword 	Yes
;; :server-name 	String 	Yes
;; :server-port 	Integer 	Yes
;; :ssl-client-cert 	java.security.cert.X509Certificate
;; :uri 	String 	Yes

(defn ->ring-map [^HTTPRequest request]
  (let [uri (URI. ^String (.getBaseURL request))]
    {:body (.getInputStream request)
    ;; headers are a Map<String, List<String>> - need to convert to Map<String, String>
     :headers (-> (into {} (.getHeaders request))
                  (update-vals (fn [val]
                                 ;; as per Ring spec - we need to convert single value lists to just the value
                                 (if (= 1 (count val))
                                   (first val)
                                   val))))
     :protocol (.getProtocol request)
     :query-string (.getQueryString request)
     :remote-addr (.getIPAddress request)
     :request-method (-> (.getMethod request) str str/lower-case keyword)
     :scheme (-> (.getScheme request) str/lower-case keyword)
     :server-name (URI/.getHost uri)
     :server-port (URI/.getPort uri)
     :uri (.getPath request)}))

(def ^:private all-content-type-header-namees ;; XXX: do we need this? it's annyoing that this is not enforced by Ring itself
  (let [basis #{"content-type" "Content-Type" "CONTENT-TYPE"}]
    (set (concat basis (mapv keyword basis)))))

(defn ->http-response [^HTTPResponse response {:keys [status body headers] :as ring-response}]
  (when status
    (.setStatus response status))
  (doseq [[k v] headers]
    ;; TODO: handle multi-value headers
    (.setHeader response (name k) (str v)))

  ;; XXX: this... is not great - but Ring does not enforce any particular case for Content-Type header(?)
  (let [content-type (some #(get headers %) all-content-type-header-namees)]
    (.setContentType response ^String (or content-type "text/plain")))

  (ring-protocols/write-body-to-stream body ring-response (.getOutputStream response)))
