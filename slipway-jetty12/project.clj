(defproject io.factorhouse/slipway-jetty12 "1.2.0"

  :description "A Clojure Companion for Jetty 12"

  :url "https://github.com/factorhouse/slipway"

  :license {:name "Apache 2.0 License"
            :url  "https://github.com/factorhouse/slipway/blob/main/LICENSE"}

  :profiles {:dev   {:dependencies   [[clj-kondo "2026.01.19"]
                                      [clj-http "3.13.1" :exclusions [commons-io]] ;; later version in reitit-ring
                                      [ch.qos.logback/logback-classic "1.5.32"]
                                      [hiccup "2.0.0"]
                                      [metosin/reitit-ring "0.10.1"]]
                     :resource-paths ["dev-resources"]
                     :plugins        [[dev.weavejester/lein-cljfmt "0.15.6"]]}
             :smoke {:pedantic? :abort}}

  :aliases {"check"  ["with-profile" "+smoke" "check"]
            "kondo"  ["with-profile" "+smoke" "run" "-m" "clj-kondo.main" "--lint" "src:test" "--parallel"]
            "fmt"    ["with-profile" "+smoke" "cljfmt" "check"]
            "fmtfix" ["with-profile" "+smoke" "cljfmt" "fix"]}

  :aot [slipway.handler.sync-handler
        slipway.websockets.adapter]

  :dependencies [[org.clojure/clojure "1.12.4"]
                 [org.clojure/tools.logging "1.3.1"]
                 [ring/ring-core "1.15.3"]
                 [ring/ring-defaults "0.7.0"]
                 [ring/ring-anti-forgery "1.4.0"]
                 [org.ring-clojure/ring-core-protocols "1.15.3"]
                 [com.taoensso/sente "1.21.0"]
                 [org.eclipse.jetty.websocket/jetty-websocket-jetty-api "12.1.7"]
                 [org.eclipse.jetty.websocket/jetty-websocket-jetty-server "12.1.7"]
                 [org.eclipse.jetty/jetty-server "12.1.7"]
                 [org.eclipse.jetty/jetty-session "12.1.7"]
                 [org.eclipse.jetty/jetty-security "12.1.7"]
                 [org.eclipse.jetty.compression/jetty-compression-server "12.1.7"]
                 [org.eclipse.jetty.compression/jetty-compression-gzip "12.1.7"]]

  :source-paths ["src"]
  :test-paths ["test"]

  :javac-options ["--release" "17"])