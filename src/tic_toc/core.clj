(ns tic-toc.core
  (:require
    [clojure.walk :refer [postwalk]]
    [tic-toc.metrics :as mtr]
    [tic-toc.timers :as tt]))

(def metrics (mtr/create-collector!))

(defn collect!*
  ([fn-id t-ns] (collect!* fn-id t-ns {}))
  ([fn-id t-ns m] (mtr/collect! metrics {:fn-name fn-id :time-ns t-ns :meta m})))

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

(defn fn-id [form] (-> form first resolve symbol (str "__") keyword gensym))

(defn inner-fn! ;; <-- make up a better name for it; (explore transient var instead of atom -- not important)
  "Saves meta-data in an atom (or somthing) and returns the form"
  [a [form meta-data]]
  (swap! a conj meta-data)
  form)

(defn wrap-tictoc*
  [form]
  `(let [fn-id# '~(fn-id form)]
    (tt/tic! fn-id#)
    (let [ret# ~form]
      (toc! fn-id#)
      ret#)))


(defmacro get-meta [form] (meta &form))


(defn outer-fn [form] [(wrap-tictoc* form) (get-meta form)])
;; TODO:
;; outer-fn should return same as wrap-meta below
;; should use get-meta macro above to the form's meta and add fn-id to it

; (defmacro wrap-meta ;; <-- this has to be made into a function!
;   [form]
;   `(let [fn-id# '~(fn-id form)]
;     ['~form (assoc ~(meta &form) :fn-id fn-id#)]))

(defn fn-call? ;; this seems to work for the 80% case
  [form]
  (if (instance? clojure.lang.PersistentList form)
    (try
      (if-let [fn-maybe (first form)]
        (ifn? (resolve fn-maybe))
        false)
      (catch Exception e false))))

(defn wrap-tictoc ;; <-- this guy now needs to walk the form with inner and outter defed above
  [form]
  (if (fn-call? form)
    (wrap-tictoc* form)
    form))

(defmacro profile [& forms] `(do ~@(map (partial postwalk wrap-tictoc) forms)))

(defn summary [] (mtr/summary metrics))

