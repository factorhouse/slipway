(ns slipway.security
  (:import (org.eclipse.jetty.security SecurityHandler)))

(defmulti ^SecurityHandler handler ::handler)

(defmethod handler :default [_] nil)

(comment
  #:slipway.security{:handler "identifies a SecurityHandler impl, :jaas', :hash, and :openid supported by default"})