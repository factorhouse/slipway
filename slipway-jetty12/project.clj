(defproject io.factorhouse/slipway-jetty12 "1.1.18"

  :description "A Clojure Companion for Jetty 12"

  :url "https://github.com/factorhouse/slipway"

  :license {:name "Apache 2.0 License"
            :url  "https://github.com/factorhouse/slipway/blob/main/LICENSE"}

  :profiles {:dev   {:dependencies   [[clj-kondo "2025.01.16"]
                                      [clj-http "3.13.0"]
                                      [ch.qos.logback/logback-classic "1.5.16"]
                                      [ring/ring-anti-forgery "1.3.1"]
                                      [metosin/reitit-ring "0.7.2"]]
                     :resource-paths ["dev-resources"]
                     :plugins        [[dev.weavejester/lein-cljfmt "0.13.0"]]}
             :smoke {:pedantic? :abort}}

  :aliases {"check" ["with-profile" "+smoke" "check"]
            "kondo" ["with-profile" "+smoke" "run" "-m" "clj-kondo.main" "--lint" "src:test" "--parallel"]
            "fmt"   ["with-profile" "+smoke" "cljfmt" "check"]}

  :dependencies [[org.clojure/clojure "1.12.0"]
                 [org.clojure/tools.logging "1.3.0"]
                 [org.ring-clojure/ring-core-protocols "1.13.0"]
                 [com.taoensso/sente "1.20.0"]
                 [org.eclipse.jetty.websocket/jetty-websocket-jetty-api "12.0.16"]
                 [org.eclipse.jetty.websocket/jetty-websocket-jetty-server "12.0.16" :exclusions [org.slf4j/slf4j-api]]
                 [org.eclipse.jetty/jetty-server "12.0.16" :exclusions [org.slf4j/slf4j-api]]
                 [org.eclipse.jetty/jetty-session "12.0.16" :exclusions [org.slf4j/slf4j-api]]
                 [org.eclipse.jetty/jetty-security "12.0.16" :exclusions [org.slf4j/slf4j-api]]

                 [org.slf4j/slf4j-api "2.0.16"]]

  :source-paths ["src"]
  :test-paths ["test"]

  :javac-options ["--release" "17"])