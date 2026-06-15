(ns slipway.security
  (:require [clojure.core.protocols :as p])
  (:import (org.eclipse.jetty.security AuthenticationState AuthenticationState$Succeeded)
           (org.eclipse.jetty.server Request)))

(defmulti handler ::handler)

(defmethod handler :default [_] nil)

(defn user
  [^Request request]
  (when-let [^AuthenticationState authentication-state (Request/getAuthenticationState request)]
    (when (instance? AuthenticationState$Succeeded authentication-state)
      (p/datafy authentication-state))))

(comment
  #:slipway.security{:handler "identifies a SecurityHandler impl, 'jaas', 'hash', and 'openid' supported by default"})