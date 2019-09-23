(ns tic-toc.timers)

(defn- now [] (System/nanoTime))
(defonce timers (atom {:default-timer nil}))

(defn tic!
  ([] (tic! :default-timer))
  ([timer]
    (swap! timers assoc timer (now))
    nil))

(defn toc
  ([] (toc :default-timer))
  ([timer] (- (now) (get @timers timer)))
  ([timer callback] (callback timers timer))) ;; did I want to add last argument here to be returned after callback?

(defn clear!
  ([] (clear! :default-timer))
  ([timer] (swap! timers dissoc timer)))