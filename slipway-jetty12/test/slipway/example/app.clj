(ns slipway.example.app
  (:refer-clojure :exclude [error-handler])
  (:require [clojure.tools.logging :as log]
            [reitit.ring :as reitit]
            [ring.middleware.anti-forgery :as ring.forgery]
            [ring.middleware.keyword-params :as ring.params.kw]
            [ring.middleware.params :as ring.params]
            [ring.middleware.session :as ring.session]
            [ring.middleware.session.memory :as ring.session.memory]
            [slipway.error :as error]
            [slipway.example.html :as html]
            [slipway.sente :as sente]
            [slipway.user :as user])
  (:import (org.eclipse.jetty.security Constraint)))

(def up (constantly {:body "" :status 200 :headers {"Content-Type" "text/plain"}}))

(defn login-handler
  [_]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (html/login-page false)})

(defn login-retry-handler
  [_]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (html/login-page true)})

(defn logout-handler
  [req]
  (user/logout req)
  {:status  302
   :headers {"location" "/"}
   :session nil})

(defn home-handler
  [_]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (html/home-page)})

(defn user-handler
  [req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (html/user-page req)})

(defn error-404-handler
  [_]
  {:status  404
   :headers {"Content-Type" "text/html"}
   :body    (html/error-page 404 "Page Not Found")})

(defn error-405-handler
  [_]
  {:status  405
   :headers {"Content-Type" "text/html"}
   :body    (html/error-page 405 "Method Not Allowed")})

(defn error-406-handler
  [_]
  {:status  406
   :headers {"Content-Type" "text/html"}
   :body    (html/error-page 406 "Not Acceptable")})

(defn deliberately-erroring-handler
  [_]
  (throw (RuntimeException. "Error Route")))

(defn server-error-body-fn
  [request code message _]
  (error/log-error request code message)
  (html/error-page code "Server Error" message))

(def server-error-handler (error/handler server-error-body-fn))

(defn routes
  [sente]
  [""
   ["/up" {:get {:handler up}}]
   ["/login" {:get {:handler login-handler}}]
   ["/login-retry" {:get {:handler login-retry-handler}}]
   ["/logout" {:get {:handler logout-handler}}]
   ["/" {:get {:handler home-handler}}]
   ["/user" {:get {:handler user-handler}}]
   ;["/chsk" {:get {:handler (:ws-handshake sente)}}]
   ["/405" {:get {:handler error-405-handler}}]
   ["/406" {:get {:handler error-406-handler}}]
   ["/500" {:get {:handler deliberately-erroring-handler}}]])

(def error-handlers
  {:not-found          error-404-handler
   :method-not-allowed error-405-handler
   :not-acceptable     error-406-handler})

(defn wrap-errors
  [handler]
  (fn [req]
    (try (handler req)
         (catch Throwable ex
           (log/errorf ex "application error %s" (:uri req))
           {:status  500
            :headers {"Content-Type" "text/html"}
            :body    (html/error-page 500 "Application Error")}))))

(defn handler
  []
  (let [sente-config   {:allowed-origins #{"http://localhost:3000"
                                           "http://localhost:3443"}}
        session-config {:store (ring.session.memory/memory-store)}]
    (-> (reitit/ring-handler
          (reitit/router (routes (sente/start-server sente-config)))
          (reitit/routes
            (reitit/create-resource-handler {:path "/"})
            (reitit/create-default-handler error-handlers)))
        (wrap-errors)
        (ring.forgery/wrap-anti-forgery)
        (ring.session/wrap-session session-config)
        (ring.params.kw/wrap-keyword-params)
        (ring.params/wrap-params))))

(def constraints
  [["/up" Constraint/ALLOWED]
   ["/css/*" Constraint/ALLOWED]
   ["/img/*" Constraint/ALLOWED]
   ["/*" Constraint/ANY_USER]])