(ns slipway.example.server.auth
  (:require [clojure.test :refer :all]
            [hiccup.core :as hiccup]
            [hiccup.page :as hiccup.page]
            [reitit.ring :as reitit.ring]
            [slipway.common.auth.constraints :as constraints]
            [slipway.example.handler :as handler]
            [slipway.server :as slipway]))

(defn login-page
  [retry?]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (hiccup/html
             (hiccup.page/html5
              {:lang "en"}
              [:head
               [:title "Login | Slipway Demo"]
               [:meta {:charset "utf-8"}]
               [:meta {:content "width=device-width, initial-scale=1, shrink-to-fit=no" :name "viewport"}]
               [:meta {:name "description" :content "A Jetty Ring adapter by the team at Factor House"}]]
              [:body
               [:div.ui.container {:style "display: flex; flex-direction: column; justify-content: center; align-items: center; min-height: 100vh"}
                [:div {:style "color: white;"}
                 (if retry?
                   [:p [:i.exclamation.triangle.icon] " Sign in failed, please retry."]
                   [:p [:i.user.outline.icon] " Sign in to Slipway Demo"])
                 [:br]
                 [:form.ui.form {:method "POST" :action "j_security_check"}
                  [:div.field
                   [:div.ui.labeled.input
                    [:div.ui.label.label {:style "color: #18364e; font-weight: normal;"} "username"]
                    [:input {:name "j_username" :type "text" :autocomplete "off" :autocapitalize "off" :autocorrect "off" :spellcheck "false" :autofocus "autofocus"}]]]
                  [:div.field
                   [:div.ui.labeled.input
                    [:div.ui.label.label {:style "color: #18364e; font-weight: normal;"} "password"]
                    [:input {:name "j_password" :type "password"}]]]
                  [:div.field [:button.ui.right.floated.blue.button {:type "submit" :style "padding: 12px"} "Sign in"]]]]]]))})

(defn error-body
  [code]
  (hiccup/html
   (hiccup.page/html5
    {:lang "en"}
    [:head
     [:title (str (case code
                    404 "Page Not Found"
                    405 "Method Not Allowed"
                    406 "Not Acceptable"
                    500 "System Error"
                    "Error") " | Slipway Demo")]
     [:meta {:charset "utf-8"}]
     [:meta {:content "width=device-width, initial-scale=1, shrink-to-fit=no" :name "viewport"}]
     [:meta {:name "description" :content "A simple, secure, self-contained toolkit for Apache Kafka®"}]]
    [:body
     [:div.ui.container {:style "display: flex; flex-direction: column; justify-content: center; align-items: center; min-height: 100vh"}
      [:div {:style "color: #c9c9c9;"}
       [:p [:i.exclamation.triangle.grey.icon] (case code
                                                 404 "404: Page Not Found"
                                                 405 "405: Method Not Allowed"
                                                 406 "406: Not Acceptable"
                                                 500 "500: System Error"
                                                 "Error")]
       [:br]]]])))

(defn error-page
  [code _]
  {:status  code
   :headers {"content-type" "text/html"}
   :body    (error-body code)})

(def routes
  [""
   ["/up" {:get {:handler (constantly {:body "" :status 200 :headers {"Content-Type" "text/plain"}})}}]
   ["/login" {:get {:handler (fn [_] (login-page false))}}]
   ["/login-retry" {:get {:handler (fn [_] (login-page true))}}]
   ["/logout" {:get {:handler (constantly {:status 302 :headers {"location" "/"} :session nil})}}]
   ["/" {:get {:handler handler/hello}}]])

;; Start a REPL with the following JVM opt:
;;  Hash User Auth: -Djava.security.auth.login.config=common/dev-resources/jaas/hash-jaas.conf or
;;  LDAP Auth:      -Djava.security.auth.login.config=common/dev-resources/jaas/ldap-jaas.conf
(def opts
  {:auth {:realm               "slipway"
          :login-uri           "/login"
          :logout-uri          "/logout"
          :login-retry-uri     "/login-retry"
          :post-login-uri-attr "org.eclipse.jetty.security.form_URI"
          :auth-method         "form"
          :auth-type           "jaas"
          :constraint-mappings (constraints/constraint-mappings
                                ["/up" (constraints/no-auth)]
                                ["/css/*" (constraints/no-auth)]
                                ["/img/*" (constraints/no-auth)]
                                ["/favicon.ico" (constraints/no-auth)]
                                ["/*" (constraints/form-auth-any-constraint)])}}) ;; first constraint wins, so this applies auth to anything not explicitly listed above

(defn handler
  []
  (reitit.ring/ring-handler
   (reitit.ring/router routes)
   (reitit.ring/routes
    (reitit.ring/create-resource-handler {:path "/"})
    (reitit.ring/create-default-handler
     {:not-found          (partial error-page 404)
      :method-not-allowed (partial error-page 405)
      :not-acceptable     (partial error-page 406)}))))

(defn server
  []
  (slipway/run-jetty (handler) opts))