(defproject io.factorhouse/slipway-jetty11 "1.0.8"

  :description "A Clojure Companion for Embedded Jetty"

  :url "https://github.com/factorhouse/slipway"

  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :profiles {:dev   {:dependencies   [[clj-kondo "2022.08.03" :exclusions [org.ow2.asm/asm]]
                                      [clj-http "3.12.3" :exclusions [commons-io]]
                                      [ch.qos.logback/logback-classic "1.2.11"]
                                      [metosin/reitit-ring "0.5.18"]]
                     :resource-paths ["dev-resources" "common/dev-resources"]
                     :plugins        [[lein-cljfmt "0.8.2"]]}
             :smoke {:pedantic? :abort}}

  :aliases {"check"  ["with-profile" "+smoke" "check"]
            "kondo"  ["with-profile" "+smoke" "run" "-m" "clj-kondo.main" "--lint" "src:test" "--parallel"]
            "fmt"    ["with-profile" "+smoke" "cljfmt" "check"]}

  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/tools.logging "1.2.4"]
                 [ring/ring-servlet "1.9.5"]
                 [ring/ring-anti-forgery "1.3.0"]
                 [com.taoensso/sente "1.17.0"]
                 [org.eclipse.jetty.websocket/websocket-jetty-api "11.0.11"]
                 [org.eclipse.jetty.websocket/websocket-jetty-server "11.0.11" :exclusions [org.slf4j/slf4j-api]]
                 [org.eclipse.jetty.websocket/websocket-servlet "11.0.11" :exclusions [org.slf4j/slf4j-api]]
                 [org.eclipse.jetty/jetty-server "11.0.11" :exclusions [org.slf4j/slf4j-api]]
                 [org.eclipse.jetty/jetty-jaas "11.0.11" :exclusions [org.slf4j/slf4j-api]]
                 [org.apache.mina/mina-core "2.1.6" :exclusions [org.slf4j/slf4j-api]] ;; explicit due to cve in 2.1.3 brought in by jetty-jaas 11.0.11
                 [org.slf4j/slf4j-api "1.7.36"]]

  :source-paths ["src" "common/src" "common-jakarta/src"]
  :java-source-paths ["common-jakarta/src-java"]
  :test-paths ["test" "common/test"])