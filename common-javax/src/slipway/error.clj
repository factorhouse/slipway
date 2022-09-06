(ns slipway.error
  (:import (java.io Writer)
           (javax.servlet.http HttpServletRequest)
           (org.eclipse.jetty.server.handler ErrorHandler)))

(defn handler ^ErrorHandler
  [body-fn]
  (proxy [ErrorHandler] []
    (writeErrorPage [^HttpServletRequest request ^Writer writer code ^String message showStacks]
      (.write writer ^String (body-fn request code message showStacks)))))