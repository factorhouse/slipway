(ns slipway.error
  (:import (jakarta.servlet.http HttpServletRequest)
           (java.io Writer)
           (org.eclipse.jetty.server.handler ErrorHandler)))

(defn handler ^ErrorHandler
  [body-fn]
  (proxy [ErrorHandler] []
    (writeErrorPage [^HttpServletRequest request ^Writer writer code ^String message showStacks]
      (.write writer ^String (body-fn request code message showStacks)))))