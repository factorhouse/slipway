(defproject io.factorhouse/slipway-jetty10 "1.1.13"

  :description "A Clojure Companion for Jetty"

  :url "https://github.com/factorhouse/slipway"

  :license {:name "MIT License"
            :url  "https://github.com/factorhouse/slipway/blob/main/LICENSE"}

  :profiles {:dev   {:dependencies   [[com.fasterxml.jackson.core/jackson-core "2.16.1"] ;; required for internal inconsistency within clj-kondo, kept at latest for CVE avoidance
                                      [clj-kondo "2023.12.15"]
                                      [clj-http "3.12.3" :exclusions [commons-io commons-codec]]
                                      [ch.qos.logback/logback-classic "1.3.14"]
                                      [ring/ring-anti-forgery "1.3.0" :exclusions [crypto-random crypto-equality]]
                                      [metosin/reitit-ring "0.6.0"]]
                     :resource-paths ["dev-resources" "common/dev-resources"]
                     :plugins        [[lein-cljfmt "0.8.2"]]}
             :smoke {:pedantic? :abort}}

  :aliases {"check" ["with-profile" "+smoke" "check"]
            "kondo" ["with-profile" "+smoke" "run" "-m" "clj-kondo.main" "--lint" "common/src:common-jetty1x/src:test:common/test" "--parallel"]
            "fmt"   ["with-profile" "+smoke" "cljfmt" "check"]}

  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/tools.logging "1.3.0"]
                 [ring/ring-servlet "1.9.6"]
                 [com.taoensso/sente "1.17.0"]
                 [org.eclipse.jetty.websocket/websocket-jetty-api "10.0.20"]
                 [org.eclipse.jetty.websocket/websocket-jetty-server "10.0.20" :exclusions [org.slf4j/slf4j-api]]
                 [org.eclipse.jetty.websocket/websocket-servlet "10.0.20" :exclusions [org.slf4j/slf4j-api]]
                 [org.eclipse.jetty/jetty-server "10.0.20" :exclusions [org.slf4j/slf4j-api]]
                 [org.eclipse.jetty/jetty-jaas "10.0.20" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/slf4j-api "2.0.12"]]

  :source-paths ["common/src" "common-jetty1x/src" "common-javax/src"]
  :test-paths ["test" "common/test"])
