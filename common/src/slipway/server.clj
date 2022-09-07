(ns slipway.server
  (:import (org.eclipse.jetty.server Connector Server)
           (org.eclipse.jetty.util.thread QueuedThreadPool ScheduledExecutorScheduler ThreadPool)))

(defmulti handler (fn [_ring_handler _login_service opts] (::handler opts)))

(defmulti connector (fn [_server opts] (keyword (namespace (first (keys opts))) "connector")))

(comment
  #:slipway.server{:handler                 ""
                   :max-threads             ""
                   :min-threads             ""
                   :threadpool-idle-timeout ""
                   :job-queue               ""
                   :daemon?                 ""
                   :thread-pool             ""
                   :error-handler           ""})

(defn create-server ^Server
  [{::keys [max-threads min-threads threadpool-idle-timeout job-queue daemon? thread-pool error-handler connectors]
    :or    {max-threads             50
            min-threads             8
            threadpool-idle-timeout 60000
            job-queue               nil
            daemon?                 false}}]
  (let [pool   (or thread-pool
                   (doto (QueuedThreadPool. (int max-threads)
                                            (int min-threads)
                                            (int threadpool-idle-timeout)
                                            job-queue)
                     (.setDaemon daemon?)))
        server (doto (Server. ^ThreadPool pool)
                 (.addBean (ScheduledExecutorScheduler.)))]
    (.setConnectors server (into-array Connector (map #(connector server %) connectors)))
    (when error-handler (.setErrorHandler server error-handler))
    server))