(defproject io.factorhouse/slipway-jetty10 "1.0.7"

  :description "A Clojure companion for Jetty by Factor House"

  :url "https://github.com/factorhouse/slipway"

  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :profiles {:dev    {:dependencies   [[clj-kondo "2022.08.03" :exclusions [org.ow2.asm/asm]]
                                       [clj-http "3.12.3" :exclusions [commons-io]]
                                       [org.slf4j/slf4j-api "1.7.36"]
                                       [ch.qos.logback/logback-classic "1.2.11"]
                                       [hiccup "1.0.5"]
                                       [metosin/reitit-ring "0.5.18"]
                                       [ring/ring-defaults "0.3.3" :exclusions [javax.servlet/javax.servlet-api]]]
                      :resource-paths ["dev-resources" "common/dev-resources"]
                      :plugins        [[lein-cljfmt "0.8.2"]]}
             :kaocha {:dependencies [[lambdaisland/kaocha "1.69.1069"]]}
             :smoke  {:pedantic? :abort}}

  :aliases {"check"  ["with-profile" "+smoke" "check"]
            "kaocha" ["with-profile" "+kaocha,+smoke" "run" "-m" "kaocha.runner"]
            "kondo"  ["with-profile" "+smoke" "run" "-m" "clj-kondo.main" "--lint" "src:test" "--parallel"]
            "fmt"    ["with-profile" "+smoke" "cljfmt" "check"]
            "fmtfix" ["with-profile" "+smoke" "cljfmt" "fix"]}

  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/tools.logging "1.2.4"]
                 [ring/ring-servlet "1.9.5"]
                 [org.eclipse.jetty.websocket/websocket-jetty-api "10.0.11"]
                 [org.eclipse.jetty.websocket/websocket-jetty-server "10.0.11" :exclusions [org.slf4j/slf4j-api]]
                 [org.eclipse.jetty.websocket/websocket-servlet "10.0.11" :exclusions [org.slf4j/slf4j-api]]
                 [org.eclipse.jetty/jetty-server "10.0.11" :exclusions [org.slf4j/slf4j-api]]
                 [org.eclipse.jetty/jetty-jaas "10.0.11" :exclusions [org.slf4j/slf4j-api]]
                 [org.apache.mina/mina-core "2.1.6"]]       ;; explicit due to cve in 2.1.3 brought in by jetty-jaas 10.0.11

  :source-paths ["src" "common/src" "common-javax/src"]
  :test-paths ["test" "common/test"])