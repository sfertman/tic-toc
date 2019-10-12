(ns tic-toc.core
  (:require
    [clojure.walk]
    [tic-toc.metrics :as mtr]
    [tic-toc.timers :as tt]))


(def metrics (mtr/create-collector!))

(defn collect!*
  ([fn-id t-ns] (collect!* fn-id t-ns {}))
  ([fn-id t-ns m] (mtr/collect! metrics {:fn-id fn-id :time-ns t-ns :meta m})))

(defn toc! ;; <-- using toc callback to clean timers up and collect metrics
  ([timer] (toc! timer {}))
  ([timer m]
  (tt/toc
    timer
    (fn [_ t] ;; <-- ignore 1st arg (timers atom)
      (let [dt (tt/toc t)]
        (tt/clear! t)
        (collect!* t dt m) ;; <-- should separate meta from timers (or mebbe not)
        dt)))))

(defn get-fn-id [form] (-> form first resolve symbol (str "__") gensym keyword))

(defn wrap-tictoc
  [form fn-id fn-meta]
  `(let [_# (tt/tic! ~fn-id)
         ret# ~form]
    (toc! ~fn-id ~fn-meta)
    ret#))

(defmacro get-meta* [form] (meta &form)) ;; works!
(defn get-meta [form] (get-meta* form)) ;; works! but problm is returns this line number! Shieeet

(defn fn-call? ;; this seems to work for the 80% case
  [form]
  (if (list? form)
    (try
      (if-let [fn-maybe (first form)]
        (ifn? (resolve fn-maybe))
        false)
      (catch Exception e false))))

(defn- push [v x] (if (vector? x) (into x v) (into [x] v)))

(defn walker
  [stack form]
  (if (coll? form)
    (let [c (count form)
          args-meta (into [] (filter some? (take c @stack)))]
      (swap! stack #(into [] (drop c %)))
      (if (fn-call? form)
        (let [fn-id (get-fn-id form)
              arg-fns (into [] (filter some? (map :fn-id args-meta)))
              fn-meta {:arg-fns arg-fns :fn-id fn-id}]
          (swap! stack push fn-meta)
          (wrap-tictoc form fn-id fn-meta))
        (do (swap! stack push args-meta)
            form)))
    (do (swap! stack push nil)
        form)))

(defn postwalk
  [f form]
  (let [stack (atom [])
        f* (partial f stack)]
    (clojure.walk/postwalk f* form)))


(defmacro profile [& forms] `(do ~@(map (partial postwalk walker) forms)))

(defn summary [] (mtr/summary @metrics))

(defn top
  ([] (top 10))
  ([n] (take n (sort-by second > (map (fn [[k v]] [k (:fn-time v)]) (summary))))))

(defn clear-session! [] (mtr/clear-all! metrics))