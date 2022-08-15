(defproject io.operatr/slipway-core "1.0.7"

  :description "A Jetty ring adapter for enterprise Clojure development."

  :url "https://github.com/operatr-io/slipway"

  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :profiles {:dev    {:dependencies [[clj-kondo "2022.08.03"]]
                      :plugins      [[lein-cljfmt "0.8.2"]]}
             :kaocha {:dependencies [[lambdaisland/kaocha "1.69.1069" :exclusions [org.clojure/tools.reader]]]}
             :smoke  {:pedantic? :abort}}

  :aliases {"check"  ["with-profile" "+smoke" "check"]
            "kaocha" ["with-profile" "+kaocha,+smoke" "run" "-m" "kaocha.runner"]
            "kondo"  ["with-profile" "+smoke" "run" "-m" "clj-kondo.main" "--lint" "src:test" "--parallel"]
            "fmt"    ["with-profile" "+smoke" "cljfmt" "check"]
            "fmtfix" ["with-profile" "+smoke" "cljfmt" "fix"]}

  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/tools.logging "1.2.4"]
                 [com.taoensso/sente "1.17.0" :scope "provided"]
                 [org.eclipse.jetty.websocket/websocket-jetty-server "10.0.11" :scope "provided"]
                 [org.eclipse.jetty/jetty-jaas "10.0.11" :scope "provided"]
                 [org.apache.mina/mina-core "2.1.6" :scope "provided"]]) ;; explicit due to cve in 2.1.3 brought in by jetty-jaas 10.0.11