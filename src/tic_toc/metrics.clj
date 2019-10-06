(ns tic-toc.metrics)

(defn create-collector! [] (atom []))
(defn collect! [a m] (swap! a conj m))
(defn clear-all! [a] (reset! a []))


(defn matcher [s] (re-matches #":(.*)__\d+$" s))
(defn get-fn-id [fn-id] (-> fn-id str matcher second))

(defn add-stat
  [X x]
  (let [tot (+ (or (:tot X) 0.0) x)
        cnt (+ (or (:cnt X) 0) 1)
        avg (/ tot cnt)]
    {:tot tot
     :cnt cnt
     :avg avg}))

(defn summarizer
  [summary metric]
  (update
    summary
    (-> metric :fn-id get-fn-id)
    add-stat
    (:time-ns metric)))

(defn summary [metrics] (reduce summarizer {} @metrics))
