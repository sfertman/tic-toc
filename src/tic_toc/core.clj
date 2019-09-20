(ns tic-toc.core
  (:require
    [clojure.walk :refer [postwalk]]))

;;; timers
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

(defmacro profile [form] (postwalk wrap-tictoc form))
