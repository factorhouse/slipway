(defproject io.factorhouse/slipway-jetty9 "1.1.21"

  :description "A Clojure Companion for Jetty 9"

  :url "https://github.com/factorhouse/slipway"

  :license {:name "Apache 2.0 License"
            :url  "https://github.com/factorhosue/slipway/blob/main/LICENSE"}

  :profiles {:dev   {:dependencies   [[clj-kondo "2025.02.20"]
                                      [clj-http "3.13.1"]
                                      [ch.qos.logback/logback-classic "1.3.15"] ;; Logback 1.3.x supports the Java EE edition whereas logback 1.4.x supports Jakarta EE, otherwise the two versions are feature identical. The 1.5.x continues the 1.4.x series but with logback-access relocated to its own repository.
                                      [ring/ring-anti-forgery "1.4.0"]
                                      [metosin/reitit-ring "0.7.2" :exclusions [ring/ring-core]]]
                     :resource-paths ["dev-resources" "common/dev-resources"]
                     :plugins        [[dev.weavejester/lein-cljfmt "0.13.1"]]}
             :smoke {:pedantic? :abort}}

  :aliases {"check" ["with-profile" "+smoke" "check"]
            "kondo" ["with-profile" "+smoke" "run" "-m" "clj-kondo.main" "--lint" "src:common/src:test:common/test" "--parallel"]
            "fmt"   ["with-profile" "+smoke" "cljfmt" "check"]}

  :dependencies [[org.clojure/clojure "1.12.2"]
                 [org.clojure/tools.logging "1.3.0"]
                 [commons-io "2.16.1"]                      ;; replaces old version with CVE in ring-servlet, remove when ring bumped to latest
                 [ring/ring-servlet "1.10.0"]
                 [com.taoensso/sente "1.17.0"]
                 [org.eclipse.jetty/jetty-server "9.4.58.v20250814"]
                 [org.eclipse.jetty.websocket/websocket-server "9.4.58.v20250814"]
                 [org.eclipse.jetty.websocket/websocket-servlet "9.4.58.v20250814"]
                 [org.apache.mina/mina-core "2.2.4"]        ;; exclude mina-core with CVE and manually bump to latest minor version
                 [org.eclipse.jetty/jetty-jaas "9.4.58.v20250814" :exclusions [org.apache.mina/mina-core]]
                 [org.slf4j/slf4j-api "2.0.17"]]

  :source-paths ["src" "common/src" "common-javax/src"]
  :test-paths ["test" "common/test"]

  :javac-options ["-target" "8" "-source" "8"])
