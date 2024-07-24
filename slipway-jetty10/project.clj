(defproject io.factorhouse/slipway-jetty10 "1.1.15"

  :description "A Clojure Companion for Jetty"

  :url "https://github.com/factorhouse/slipway"

  :license {:name "MIT License"
            :url  "https://github.com/factorhouse/slipway/blob/main/LICENSE"}

  :profiles {:dev   {:dependencies   [[com.fasterxml.jackson.core/jackson-core "2.16.2"] ;; required for internal inconsistency within clj-kondo, kept at latest for CVE avoidance
                                      [clj-kondo "2023.12.15"]
                                      [clj-http "3.13.0"]
                                      [ch.qos.logback/logback-classic "1.3.14"] ;; Logback 1.3.x supports the Java EE edition whereas logback 1.4.x supports Jakarta EE, otherwise the two versions are feature identical. The 1.5.x continues the 1.4.x series but with logback-access relocated to its own repository.
                                      [ring/ring-anti-forgery "1.3.1"]
                                      [metosin/reitit-ring "0.7.1" :exclusions [ring/ring-core]]]
                     :resource-paths ["dev-resources" "common/dev-resources"]
                     :plugins        [[lein-cljfmt "0.9.2"]]}
             :smoke {:pedantic? :abort}}

  :aliases {"check" ["with-profile" "+smoke" "check"]
            "kondo" ["with-profile" "+smoke" "run" "-m" "clj-kondo.main" "--lint" "common/src:common-jetty1x/src:test:common/test" "--parallel"]
            "fmt"   ["with-profile" "+smoke" "cljfmt" "check"]}

  :dependencies [[org.clojure/clojure "1.11.3"]
                 [org.clojure/tools.logging "1.3.0"]
                 [ring/ring-servlet "1.9.6"]
                 [com.taoensso/sente "1.17.0"]
                 [org.eclipse.jetty.websocket/websocket-jetty-api "10.0.22"]
                 [org.eclipse.jetty.websocket/websocket-jetty-server "10.0.22" :exclusions [org.slf4j/slf4j-api]]
                 [org.eclipse.jetty.websocket/websocket-servlet "10.0.22" :exclusions [org.slf4j/slf4j-api]]
                 [org.eclipse.jetty/jetty-server "10.0.22" :exclusions [org.slf4j/slf4j-api]]
                 [org.eclipse.jetty/jetty-jaas "10.0.22" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/slf4j-api "2.0.13"]]

  :source-paths ["common/src" "common-jetty1x/src" "common-javax/src"]
  :test-paths ["test" "common/test"])
