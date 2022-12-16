(defproject io.factorhouse/slipway-jetty9 "1.1.5"

  :description "A Clojure Companion for Jetty"

  :url "https://github.com/factorhouse/slipway"

  :license {:name "MIT License"
            :url  "https://github.com/factorhosue/slipway/blob/main/LICENSE"}

  :profiles {:dev   {:dependencies   [[clj-kondo "2022.12.08"]
                                      [clj-http "3.12.3" :exclusions [commons-io]]
                                      [ch.qos.logback/logback-classic "1.2.11"]
                                      [ring/ring-anti-forgery "1.3.0" :exclusions [crypto-random]]
                                      [metosin/reitit-ring "0.5.18"]]
                     :resource-paths ["dev-resources" "common/dev-resources"]
                     :plugins        [[lein-cljfmt "0.8.2"]]}
             :smoke {:pedantic? :abort}}

  :aliases {"check" ["with-profile" "+smoke" "check"]
            "kondo" ["with-profile" "+smoke" "run" "-m" "clj-kondo.main" "--lint" "src:common/src:test:common/test" "--parallel"]
            "fmt"   ["with-profile" "+smoke" "cljfmt" "check"]}

  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/tools.logging "1.2.4"]
                 [ring/ring-servlet "1.9.5"]
                 [com.taoensso/sente "1.17.0"]
                 [org.eclipse.jetty/jetty-server "9.4.50.v20221201"]
                 [org.eclipse.jetty.websocket/websocket-server "9.4.50.v20221201"]
                 [org.eclipse.jetty.websocket/websocket-servlet "9.4.50.v20221201"]
                 [org.eclipse.jetty/jetty-jaas "9.4.50.v20221201"]
                 [org.slf4j/slf4j-api "1.7.36"]]

  :source-paths ["src" "common/src" "common-javax/src"]
  :test-paths ["test" "common/test"]

  :javac-options ["-target" "8" "-source" "8"])
