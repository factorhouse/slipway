(defproject io.factorhouse/slipway-jetty9 "1.1.15"

  :description "A Clojure Companion for Jetty"

  :url "https://github.com/factorhouse/slipway"

  :license {:name "MIT License"
            :url  "https://github.com/factorhosue/slipway/blob/main/LICENSE"}

  :profiles {:dev   {:dependencies   [[com.fasterxml.jackson.core/jackson-core "2.16.2"] ;; required for internal inconsistency within clj-kondo, kept at latest for CVE avoidance
                                      [clj-kondo "2023.12.15"] ;; https://github.com/clj-kondo/clj-kondo/issues/2277 leave at this version until we move to Clojure 1.12.x and can fix
                                      [clj-http "3.13.0"]
                                      [ch.qos.logback/logback-classic "1.3.14"] ;; Logback 1.3.x supports the Java EE edition whereas logback 1.4.x supports Jakarta EE, otherwise the two versions are feature identical. The 1.5.x continues the 1.4.x series but with logback-access relocated to its own repository.
                                      [ring/ring-anti-forgery "1.3.1"]
                                      [metosin/reitit-ring "0.7.1" :exclusions [ring/ring-core]]]
                     :resource-paths ["dev-resources" "common/dev-resources"]
                     :plugins        [[lein-cljfmt "0.9.2"]]}
             :smoke {:pedantic? :abort}}

  :aliases {"check" ["with-profile" "+smoke" "check"]
            "kondo" ["with-profile" "+smoke" "run" "-m" "clj-kondo.main" "--lint" "src:common/src:test:common/test" "--parallel"]
            "fmt"   ["with-profile" "+smoke" "cljfmt" "check"]}

  :dependencies [[org.clojure/clojure "1.11.3"]
                 [org.clojure/tools.logging "1.3.0"]
                 [ring/ring-servlet "1.9.6"]
                 [com.taoensso/sente "1.17.0"]
                 [org.eclipse.jetty/jetty-server "9.4.55.v20240627"]
                 [org.eclipse.jetty.websocket/websocket-server "9.4.55.v20240627"]
                 [org.eclipse.jetty.websocket/websocket-servlet "9.4.55.v20240627"]
                 [org.eclipse.jetty/jetty-jaas "9.4.55.v20240627"]
                 [org.slf4j/slf4j-api "2.0.13"]]

  :source-paths ["src" "common/src" "common-javax/src"]
  :test-paths ["test" "common/test"]

  :javac-options ["-target" "8" "-source" "8"])
