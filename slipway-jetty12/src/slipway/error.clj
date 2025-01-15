(ns slipway.error
  (:require [clojure.tools.logging :as log])
  (:import (java.io Writer)
           (org.eclipse.jetty.server Request)
           (org.eclipse.jetty.server.handler ErrorHandler)))

(defn log-error
  [^Request request code message]
  (if-let [ex nil                                           ; TODO: where now? (.getAttribute request RequestDispatcher/ERROR_EXCEPTION)
           ]
    (log/errorf ex "server error: %s %s" code message)
    (log/errorf "server error: %s %s" code message)))

(defn handler ^ErrorHandler
  [body-fn]
  (proxy [ErrorHandler] []
    (writeErrorHtml [^Request request ^Writer writer code ^String message showStacks]
      (.write writer ^String (body-fn request code message showStacks)))))