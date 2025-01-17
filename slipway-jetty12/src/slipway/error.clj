(ns slipway.error
  (:require [clojure.tools.logging :as log])
  (:import (java.io Writer)
           (java.nio.charset Charset)
           (org.eclipse.jetty.server Request)
           (org.eclipse.jetty.server.handler ErrorHandler)))

(defn log-error
  [^Request code message cause]
  (if cause
    (log/errorf cause "server error: %s %s" code message)
    (log/errorf "server error: %s %s" code message)))

(defn handler ^ErrorHandler
  [body-fn]
  (proxy [ErrorHandler] []
    (writeErrorHtml [^Request request ^Writer writer ^Charset charset code ^String message ^Throwable cause show-stacks]
      (.write writer ^String (body-fn request charset code message cause show-stacks)))))