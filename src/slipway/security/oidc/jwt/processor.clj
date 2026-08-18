(ns slipway.security.oidc.jwt.processor
  (:gen-class
   :name slipway.security.oidc.jwt.JWTProcessorBean
   :extends org.eclipse.jetty.util.component.AbstractLifeCycle
   :state state
   :init init
   :constructors {[clojure.lang.IFn clojure.lang.IFn]
                  []}
   :methods [[getProcessor [] com.nimbusds.jwt.proc.JWTProcessor]]
   :prefix "-"))

(defn -init
  [start-fn stop-fn]
  [[] (atom {:start-fn start-fn :stop-fn stop-fn})])

(defn -doStart
  [this]
  (let [state    (.state this)
        start-fn (:start-fn @state)
        started  (start-fn)]
    (swap! state merge started)))

(defn -doStop
  [this]
  (let [state   (.state this)
        stop-fn (:stop-fn @state)]
    (stop-fn (:jwk-source @state))))

(defn -getProcessor
  [this]
  (:processor @(.state this)))