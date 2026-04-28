(defproject io.factorhouse/slipway-jetty12 "2.0.1"

  :description "A Clojure Companion for Jetty 12"

  :url "https://github.com/factorhouse/slipway"

  :license {:name "Apache 2.0 License"
            :url  "https://github.com/factorhouse/slipway/blob/main/LICENSE"}

  :profiles {:dev      {:dependencies   [[clj-kondo "2026.04.15"]
                                         [clj-http "3.13.1" :exclusions [commons-io]] ;; later version in reitit-ring
                                         [ch.qos.logback/logback-classic "1.5.32"]
                                         [hiccup "2.0.0"]
                                         [ring/ring-core "1.15.4"]
                                         [ring/ring-anti-forgery "1.4.0"]
                                         [metosin/reitit-ring "0.10.1"]]
                        :resource-paths ["dev-resources"]
                        :plugins        [[dev.weavejester/lein-cljfmt "0.16.3"]]}
             :pedantic {:pedantic? :abort}}

  :aliases {"check"  ["with-profile" "+pedantic" "check"]
            "kondo"  ["with-profile" "+pedantic" "run" "-m" "clj-kondo.main" "--lint" "src:test" "--parallel"]
            "fmt"    ["with-profile" "+pedantic" "cljfmt" "check"]
            "fmtfix" ["with-profile" "+pedantic" "cljfmt" "fix"]}

  :aot [slipway.handler.sync-handler]

  :dependencies [[org.clojure/clojure "1.12.4"]
                 [org.clojure/tools.logging "1.3.1"]
                 [org.ring-clojure/ring-core-protocols "1.15.4"]
                 [com.taoensso/sente "1.21.0"]
                 [org.eclipse.jetty.websocket/jetty-websocket-jetty-api "12.1.8"]
                 [org.eclipse.jetty.websocket/jetty-websocket-jetty-server "12.1.8"]
                 [org.eclipse.jetty/jetty-server "12.1.8"]
                 [org.eclipse.jetty/jetty-session "12.1.8"]
                 [org.eclipse.jetty/jetty-security "12.1.8"]
                 [org.eclipse.jetty.compression/jetty-compression-server "12.1.8"]
                 [org.eclipse.jetty.compression/jetty-compression-gzip "12.1.8"]]

  :source-paths ["src"]
  :test-paths ["test"]

  :javac-options ["--release" "17"])
