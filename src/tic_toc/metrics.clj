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

(defn matcher [s] (re-matches #":(.*)__\d+$" s))
(defn get-fn-name [fn-id] (-> fn-id str matcher second))

(defn summary
  [metrics]
  (let [index (index-metrics metrics)
        arg-fn-time (memoize
                      (fn [fn-name] ;; TODO: later should be done for fn-id if we're tracking line numbers
                        (->>
                          fn-name
                          (get index)
                          (map #(nth metrics %))
                          (map :time-ns)
                          (reduce +))))
        args-time (fn [metric]
                    (if-let [arg-fns (-> metric :meta :arg-fns seq)]
                      (reduce + (map arg-fn-time arg-fns))
                      0))
        add-stat (fn [old-val x]
                    (let [total-time (+ (or (:total-time old-val) 0) (:time-ns x))
                          args-time* (args-time x)
                          fn-time (- total-time args-time*)
                          calls (+ (or (:calls old-val) 0) 1)]
                      {:total-time total-time
                      :args-time args-time*
                      :fn-time fn-time
                      :calls calls
                      :mean-total (double (/ total-time calls))
                      :mean-args (double (/ args-time* calls))
                      :mean-fn (double (/ fn-time calls))}))
        summarizer (fn [s m] (update s (-> m :fn-id get-fn-name) add-stat m))]
    (reduce summarizer {} metrics)))