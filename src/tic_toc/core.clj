(ns tic-toc.core
  (:require
    [clojure.walk :refer [postwalk]]
    [tic-toc.metrics :as mtr]
    [tic-toc.timers :as tt]))

(def metrics (mtr/create-collector!))

(defn collect!*
  ([fnk tns] (collect!* fnk tns {}))
  ([fnk tns m] (mtr/collect! metrics {:fn-name fnk :time-ns tns :meta m})))

(defn toc! ;; <-- using toc callback to clean timers up and collect metrics
  [timer]
  (tt/toc
    timer
    (fn [_ t] ;; <-- ignore 1st arg (timers atom)
      (let [dt (tt/toc t)]
        (tt/clear! t)
        (collect!* t dt)
        dt))))

(defn fn-key [form] (-> form first resolve symbol (str "__") keyword gensym))

(defn wrap-tictoc*
  [form]
  `(let [fnk# '~(fn-key form)]
    (tt/tic! fnk#)
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

(defn summary [] (mtr/summary metrics))

