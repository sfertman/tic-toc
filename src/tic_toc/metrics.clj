(ns tic-toc.metrics)

(defn create-collector! [] (atom []))
(defn collect! [a m] (swap! a conj m))
(defn clear-all! [a] (reset! a []))


(defn matcher [s] (re-matches #":(.*)__\d+$" s))
(defn get-fn-name [fn-id] (-> fn-id str matcher second))

(defn add-stat
  [X x]
  (let [tot (+ (or (:tot X) 0.0) x)
        calls (+ (or (:calls X) 0) 1)
        avg (/ tot calls)]
    {:tot tot
     :calls calls
     :avg avg}))

(defn summarizer
  [summary metric]
  (update
    summary
    (-> metric :fn-id get-fn-name)
    add-stat
    (:time-ns metric)))

(defn summary [metrics] (reduce summarizer {} @metrics))
