(defproject io.factorhouse/slipway-jetty12 "1.1.18"

  :description "A Clojure Companion for Jetty 12"

  :url "https://github.com/factorhouse/slipway"

  :license {:name "Apache 2.0 License"
            :url  "https://github.com/factorhouse/slipway/blob/main/LICENSE"}

  :profiles {:dev   {:dependencies   [[com.fasterxml.jackson.core/jackson-core "2.18.2"] ;; required for internal inconsistency within clj-kondo, kept at latest for CVE avoidance
                                      [clj-kondo "2024.11.14"]
                                      [clj-http "3.13.0"]
                                      [ch.qos.logback/logback-classic "1.3.14"] ;; Logback 1.3.x supports the Java EE edition whereas logback 1.4.x supports Jakarta EE, otherwise the two versions are feature identical. The 1.5.x continues the 1.4.x series but with logback-access relocated to its own repository.
                                      [ring/ring-anti-forgery "1.3.1"]
                                      [metosin/reitit-ring "0.7.2" :exclusions [ring/ring-core]]]
                     :resource-paths ["dev-resources" "common/dev-resources"]
                     :plugins        [[lein-cljfmt "0.9.2"]]}
             :smoke {:pedantic? :abort}}

  :aliases {"check" ["with-profile" "+smoke" "check"]
            "kondo" ["with-profile" "+smoke" "run" "-m" "clj-kondo.main" "--lint" "common/src:common-jetty1x/src:test:common/test" "--parallel"]
            "fmt"   ["with-profile" "+smoke" "cljfmt" "check"]}

  :dependencies [[org.clojure/clojure "1.12.0"]
                 [org.clojure/tools.logging "1.3.0"]
                 [commons-io "2.16.1"]                      ;; replaces old version with CVE in ring-servlet, remove when ring bumped to latest
                 [org.ring-clojure/ring-jakarta-servlet "1.13.0"]
                 [com.taoensso/sente "1.17.0"]
                 [org.eclipse.jetty.websocket/jetty-websocket-jetty-api "12.0.16"]
                 [org.eclipse.jetty.websocket/jetty-websocket-jetty-server "12.0.16" :exclusions [org.slf4j/slf4j-api]]
                 [org.eclipse.jetty/jetty-server "12.0.16" :exclusions [org.slf4j/slf4j-api]]
                 [org.eclipse.jetty/jetty-security "12.0.16" :exclusions [org.slf4j/slf4j-api]]
                 [org.eclipse.jetty.ee10/jetty-ee10-servlet "12.0.16"]
                 [org.slf4j/slf4j-api "2.0.16"]]

  :source-paths ["src" "common/src" "common-jetty1x/src" "common-javax/src"]
  :test-paths ["common/test"]

  :javac-options ["--release" "17"])