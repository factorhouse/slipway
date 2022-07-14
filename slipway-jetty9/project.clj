(defproject io.operatr/slipway-jetty9 "1.0.7"
  :description "A Jetty ring adapter for enterprise Clojure development."
  :url "https://github.com/operatr-io/slipway"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :profiles {:dev    {:dependencies   [[clj-kondo "2022.06.22"]
                                       [clj-http "3.12.3"]
                                       [commons-io "2.11.0"]]
                      :resource-paths ["dev-resources"]
                      :plugins        [[lein-cljfmt "0.8.0"]]}
             :kaocha {:dependencies [[lambdaisland/kaocha "1.68.1059"]]}
             :smoke  {:pedantic? :abort}}
  :aliases {"check"  ["with-profile" "+smoke" "check"]
            "kaocha" ["with-profile" "+kaocha,+smoke" "run" "-m" "kaocha.runner"]
            "kondo"  ["with-profile" "+smoke" "run" "-m" "clj-kondo.main" "--lint" "src:test" "--parallel"]
            "fmt"    ["with-profile" "+smoke" "cljfmt" "check"]
            "fmtfix" ["with-profile" "+smoke" "cljfmt" "fix"]}

  :dependencies [[org.clojure/clojure "1.11.1"]
                 [ring/ring-servlet "1.9.5"]
                 [io.operatr/slipway-core "1.0.7"]
                 [org.eclipse.jetty/jetty-server "9.4.48.v20220622"]
                 [org.eclipse.jetty.websocket/websocket-server "9.4.48.v20220622"]
                 [org.eclipse.jetty.websocket/websocket-servlet "9.4.48.v20220622"]
                 [org.eclipse.jetty/jetty-alpn-server "9.4.48.v20220622"]
                 [org.eclipse.jetty/jetty-jaas "9.4.48.v20220622"]])
