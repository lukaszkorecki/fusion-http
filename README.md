# `fusion-http`

## What is it?

`fusion-http` implements a Ring adapter for [FusionAuth's `java-http` server](https://github.com/FusionAuth/java-http) - a high performance, plain Java, minimal, yet usable HTTP server.
Performance is achieved by relying on Project Loom (Virtual Threads). That in turn greatly reduces complexity and the API of the whole project.

> The `java-http` package is also planning an HTTP client, so when that lands, `fusion-http` might wrap that too, although projects like Hato already exist.

## Why?

Minimal footprint and zero dependencies make `fusion-http` a good target for creating APIs and servers in Clojure that can be compiled to native binaries using GraalVM's native-image.
Secondly, `fusion-http` is much smaller than Jetty and comes with less baggage because of that. Conversely, there are missing features worth mentioning:

- no websocket support - not a deal breaker for most, but YMMV
- only synchronous Ring handlers are supported - using virtual threads as the backbone mitigates the need for an async-style API however, it also means that certain HTTP protocol features are not supported such as SSE (server-sent events) due to how the request lifecycle is handled in `java-http`


## Getting started

- add this project as dependency
- use the adapter:


```clojure
(def app
  (-> (fn [request]
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body {:message "hello" }})

      (ring-json/wrap-json-body {:keywords? true})
      (ring-defaults/wrap-defaults ring-defaults/api-defaults)
      (ring-json/wrap-json-response)))

(def server
  (fusion-http.server/create app {:port 3000}))
```

To stop the server, invoke `.close` on the instance. Because `HTTPServer` class implements `AutoCloseable`, `with-open` will also work. The server will not block the main thread so it's best to use it with Component or something similar.

## Progress

- [x] make it work with a CRUD API implemented using typical Ring stack (core, middlewares, defaults, Ring-compat router and JSON based serde)
- [x] verify it works over HTTP
- [ ] finalize naming of things
- [ ] more docs, doc strings, recipes
