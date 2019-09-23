(ns tic-toc.core
  (:require
    [clojure.walk :refer [postwalk]]
    [tic-toc.timers :refer :all]))

;;; metrics
(defonce metrics (atom []))

(defn collect! [m] (swap! metrics conj m))
(defn collect!* [fnk tns] (collect! {:fn-name fnk :time-ns tns}))

(defn toc! ;; <-- using toc callback to clean timers up and collect metrics
  [timer]
  (toc
    timer
    (fn [_ t]
      (let [dt (toc t)]
        (clear! t)
        (collect!* t dt)
        dt))))

(defn fn-key [form] (-> form first resolve symbol (str "__") keyword gensym))

(defn wrap-tictoc*
  [form]
  `(let [fnk# '~(fn-key form)]
    (tic! fnk#)
    (let [ret# ~form]
      (toc! fnk#)
      ret#)))

(defn fn-call? ;; this seems to work for the 80% case
  [form]
  (if (instance? clojure.lang.PersistentList form)
    (try
      (if-let [fn-maybe (first form)]
        (ifn? (resolve fn-maybe))
        false)
      (catch Exception e false))))

(defn wrap-tictoc
  [form]
  (if (fn-call? form)
    (wrap-tictoc* form)
    form))

(defmacro profile [& forms] `(do ~@(map (partial postwalk wrap-tictoc) forms)))

(defn matcher [s] (re-matches #":(.*)__\d+$" s))
(defn fn-name [fnk] (-> fnk str matcher second))

(defn add-stat
  [X x]
  (let [tot (+ (or (:tot X) 0.0) x)
        cnt (+ (or (:cnt X) 0) 1)
        avg (/ tot cnt)]
    {:tot tot
     :cnt cnt
     :avg avg}))

(defn summarizer [m m*] (update m (-> m* :fn-name fn-name) add-stat (:time-ns m*)))

(defn summary [mtr] (reduce summarizer {} @mtr))
