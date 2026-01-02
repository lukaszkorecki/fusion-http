(ns fusion-http.impl.logger
  "Sets up a bridge between java-http logging and slf4j logging - this makes it possible to use any slf4j-compatible logging framework (Logback, Log4J, etc.) and clojure.tools.logging")

(set! *warn-on-reflection* true)

(defn slf4j-logger
  "Creates a FusionAuth Logger backed by SLF4J"
  ^io.fusionauth.http.log.Logger
  [^Class klass]
  (let [slf4j-log (org.slf4j.LoggerFactory/getLogger klass)]
    ;; supported methods on FusionAuth Logger interface -> slf4j Logger interface
    (reify io.fusionauth.http.log.Logger

      ;;  void debug(String message); --> void debug(String message);
      ;;  void debug(String message, Object... values); -->  void debug(String message, Object... values);
      ;;  void debug(String message, Throwable throwable); -->  void debug(String message, Throwable throwable);

      (^void debug [_this ^String message]
        (org.slf4j.Logger/.debug slf4j-log message))
      (^void debug [_this ^String message ^java.lang.Object/1 values]
        (org.slf4j.Logger/.debug slf4j-log message (to-array values)))
      (^void debug [_this ^String message ^Throwable throwable]
        (org.slf4j.Logger/.debug slf4j-log message throwable))

      ;;  void info(String message); -->  void info(String)
      ;;  void info(String message, Object... values); -->  void info(String)
      (^void info [_this ^String message]
        (org.slf4j.Logger/.info slf4j-log message))
      (^void info [_this ^String message ^java.lang.Object/1 values]
        (org.slf4j.Logger/.info slf4j-log message (to-array values)))

      ;;  void error(String message, Throwable throwable); -->  void error(String message, Throwable throwable);
      ;;  void error(String message); -->  void error(String message);
      (^void error [_this ^String message]
        (org.slf4j.Logger/.error slf4j-log message))
      (^void error [_this ^String message ^Throwable throwable]
        (org.slf4j.Logger/.error slf4j-log message throwable))

      ;; void trace(String message, Object... values); -->  void trace(String message, Object... values);
      ;; void trace(String message); -->  void trace(String message)
      (^void trace [_this ^String message]
        (org.slf4j.Logger/.trace slf4j-log message))
      (^void trace [_this ^String message ^java.lang.Object/1 values]
        (org.slf4j.Logger/.trace slf4j-log message (to-array values)))

      ;; NOTE: there's a default impl isEnabledForLevel which delegates to isXEnabled methods
      (^boolean isDebugEnabled [_]
        (org.slf4j.Logger/.isDebugEnabled slf4j-log))

      (^boolean isInfoEnabled [_]
        (org.slf4j.Logger/.isInfoEnabled slf4j-log))

      (^boolean isErrorEnabled [_]
        (org.slf4j.Logger/.isErrorEnabled slf4j-log))

      (^boolean isTraceEnabled [_]
        (org.slf4j.Logger/.isTraceEnabled slf4j-log))

      ;; SLF4J doesn't typically support runtime level changes
      ;; This is a no-op unless you're using a specific SLF4J implementation
      ;; that supports it (like Logback with ch.qos.logback.classic.Logger)
      (^void setLevel [_this ^io.fusionauth.http.log.Level _level]
        nil))))

(defn factory
  "Creates a FusionAuth LoggerFactory backed by SLF4J.
   Returns a reified LoggerFactory instance."
  []
  (reify io.fusionauth.http.log.LoggerFactory
    (^io.fusionauth.http.log.Logger getLogger [_ ^Class klass]
      (slf4j-logger klass))))
