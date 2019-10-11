(ns tic-toc.metrics)

(defn create-collector! [] (atom []))
(defn collect! [a m] (swap! a conj m))
(defn clear-all! [a] (reset! a []))

(defn- upsert
  [m [k v]]
  (if (some? (k m))
    (update m k conj v)
    (assoc m k [v])))

(defn index-metrics
  "Returns a map of function ids to their positions in the metrics array"
  [metrics]
  (reduce upsert {} (map-indexed (fn [i metric] [(:fn-id metric) i] ) metrics)))

(defn- args-time*
  "Calculates argument time for a given metric"
  [metrics index metric]
  ;; ^^ may be problematic with recursion; possibly need to track; unique fn-id might save us here; test this to find out!
  (if-let [arg-fns (-> metric :meta :arg-fns)]
    (let [arg-fns-idx (reduce into [] (map (fn [fn-id] (fn-id index)) arg-fns))
          arg-fns-metrics (map (partial nth metrics) arg-fns-idx)
          arg-fns-times (map :time-ns arg-fns-metrics)]
      (apply + arg-fns-times))
    0))
(def args-time (memoize args-time*))

(defn matcher [s] (re-matches #":(.*)__\d+$" s))
(defn get-fn-name [fn-id] (-> fn-id str matcher second))

(defn add-stat
  [metrics index X x]
  (let [total-time (+ (or (:total-time X) 0) (:time-ns x))
        args-time* (args-time metrics index x)
        ;; TODO: ^^ this is called multiple times to get same result; memoize?
        fn-time (- total-time args-time*)
        calls (+ (or (:calls X) 0) 1)
        mean-total (double (/ total-time calls))
        mean-args (double (/ args-time* calls))
        mean-fn (double (/ fn-time calls))]
    {:total-time total-time
     :args-time args-time*
     :fn-time fn-time
     :calls calls
     :mean-total mean-total
     :mean-args mean-args
     :mean-fn mean-fn}))

(defn summarizer
  [metrics index summary metric]
  (let [add-stat* (partial add-stat metrics index)]
    (update
      summary
      (-> metric :fn-id get-fn-name)
      add-stat*
      metric)))

(defn- summary* [metrics]
  (let [summarizer* (partial summarizer @metrics (index-metrics @metrics))]
    (reduce summarizer* {} @metrics)))

(def summary (memoize summary*))
