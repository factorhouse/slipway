(ns slipway.error
  (:require [clojure.tools.logging :as log])
  (:import (jakarta.servlet RequestDispatcher)
           (jakarta.servlet.http HttpServletRequest)
           (java.io Writer)
           (org.eclipse.jetty.server.handler ErrorHandler)))

(defn log-error
  [^HttpServletRequest request code message]
  (if-let [ex (.getAttribute request RequestDispatcher/ERROR_EXCEPTION)]
    (log/errorf ex "server error: %s %s" code message)
    (log/errorf "server error: %s %s" code message)))

(defn handler ^ErrorHandler
  [body-fn]
  (proxy [ErrorHandler] []
    (writeErrorPage [^HttpServletRequest request ^Writer writer code ^String message showStacks]
      (.write writer ^String (body-fn request code message showStacks)))))