(defproject io.operatr/slipway-jetty10 "1.0.3"
  :description "A Jetty ring adapter for enterprise Clojure development."
  :url "https://github.com/operatr-io/slipway"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :profiles {:dev    {:dependencies   [[clj-kondo "2022.03.09"]
                                       [clj-http "3.12.3"]
                                       [commons-io "2.10.0"]]
                      :resource-paths ["dev-resources"]
                      :plugins        [[lein-cljfmt "0.8.0"]]}
             :kaocha {:dependencies [[lambdaisland/kaocha "1.64.1010"]]}
             :smoke  {:pedantic? :abort}}
  :aliases {"check"  ["with-profile" "+smoke" "check"]
            "kaocha" ["with-profile" "+kaocha,+smoke" "run" "-m" "kaocha.runner"]
            "kondo"  ["with-profile" "+smoke" "run" "-m" "clj-kondo.main" "--lint" "src:test" "--parallel"]
            "fmt"    ["with-profile" "+smoke" "cljfmt" "check"]
            "fmtfix" ["with-profile" "+smoke" "cljfmt" "fix"]}

  :dependencies [[org.clojure/clojure "1.11.0"]
                 [org.clojure/tools.logging "1.2.4"]
                 [ring/ring-servlet "1.9.5"]
                 [io.operatr/slipway-core "1.0.3"]
                 [org.eclipse.jetty.websocket/websocket-jetty-api "10.0.9" :exclusions [org.slf4j/slf4j-api]]
                 [org.eclipse.jetty.websocket/websocket-jetty-server "10.0.9" :exclusions [org.slf4j/slf4j-api]]
                 [org.eclipse.jetty.websocket/websocket-servlet "10.0.9" :exclusions [org.slf4j/slf4j-api]]
                 [org.eclipse.jetty/jetty-alpn-server "10.0.9" :exclusions [org.slf4j/slf4j-api]]
                 [org.eclipse.jetty/jetty-alpn-java-server "10.0.9" :exclusions [org.slf4j/slf4j-api]]
                 [org.eclipse.jetty/jetty-server "10.0.9" :exclusions [org.slf4j/slf4j-api]]
                 [org.eclipse.jetty/jetty-jaas "10.0.9" :exclusions [org.slf4j/slf4j-api]]
                 ;; Explicit due to cve in 2.1.3 brought in by jetty-jaas 10.0.9 (three minor bumps should be fine)
                 [org.apache.mina/mina-core "2.1.6"]])
