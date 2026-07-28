(ns slipway.lifecycle
  (:gen-class
   :name slipway.lifecycle.ManagedState
   :extends org.eclipse.jetty.util.component.AbstractLifeCycle
   :state state
   :init init
   :constructors {[clojure.lang.IFn clojure.lang.IFn]
                  []}
   :methods [[getStartedState [] java.lang.Object]]
   :prefix "-"))

(defn -init
  [start-fn stop-fn]
  [[] (atom {:start-fn start-fn :stop-fn stop-fn})])

(defn -doStart
  [this]
  (let [state    (.state this)
        start-fn (:start-fn @state)
        started  (start-fn)]
    (swap! state assoc :started started)))

(defn -doStop
  [this]
  (let [state   (.state this)
        stop-fn (:stop-fn @state)]
    (stop-fn (:started @state))))

(defn -getStartedState
  [this]
  (:started @(.state this)))