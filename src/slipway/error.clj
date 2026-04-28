(ns slipway.error
  (:require [clojure.tools.logging :as log])
  (:import (java.io Writer)
           (java.nio.charset Charset)
           (org.eclipse.jetty.http HttpStatus)
           (org.eclipse.jetty.server Request)
           (org.eclipse.jetty.server.handler ErrorHandler)))

(defn log-error
  [code uri message cause]
  (if cause
    (log/errorf cause "server error: %s %s %s" code uri message)
    (log/errorf "server error: %s %s %s" code uri message)))

(defn handler ^ErrorHandler
  [body-fn]
  (proxy [ErrorHandler] []
    (writeErrorHtml [^Request request ^Writer writer ^Charset charset code ^String message ^Throwable cause]
      (let [message (or message (HttpStatus/getMessage code))
            uri     (-> (.getHttpURI request) (.toString))]
        (.write writer ^String (body-fn request charset code uri message cause))))))