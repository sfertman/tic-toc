(ns tic-toc.core
  (:require
    [clojure.pprint :refer [pprint]] ;; remove in release
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

(defn wrap-tictoc* ;; this on is the one that just works
  [form]
  `(let [fn-id# '~(fn-id form)]
    (tt/tic! fn-id#)
    (let [ret# ~form]
      (toc! fn-id#)
      ret#)))

(defn wrap-tictoc** ;; this on is with met data
  [form fn-id meta-data]
  `(do
    (tt/tic! ~fn-id)
    (let [ret# ~form]
      (toc! ~fn-id ~meta-data)
      ret#)))


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

(defn wrap-tictoc ;; <-- this guy now needs to walk the form with inner and outter defed above
  [form]
  (if (fn-call? form)
    (wrap-tictoc* form)
    form))

(defmacro profile [& forms] `(do ~@(map (partial postwalk wrap-tictoc) forms)))

(defn summary [] (mtr/summary metrics))


;; GIANT TODO
;; this is the latest and greatest
;; it uses a stack to track meta and this does not modify the forms
;; it supports any clojure form that walk does simply because it doesn't
;; try to get smart and uses postwalk
;; ~~1. fix the damn logic here -- something went wrong deleting prn outputs~~
;; ~~2. plug the correct tictoc wrapper whatver it is~~
;; ~~3. this is very cool but meta dosn't propagate through more than one layer -- fix this and it's all done!~~
;; 4. organize this file and delete everything that's not needed
;; 5. test
;; 6. test

(defn walker
  [stack form]
  (if (coll? form)
    (let [c (count form)
          args-meta (filter some? (take c @stack))]
      (swap! stack #(drop c %))
      (if (fn-call? form)
        (let [fn-id' (fn-id form)
              meta' (filter some? (map :fn-id args-meta))
              meta'' (list {:arg-fns meta' :fn-id fn-id'})]
          (swap! stack into meta'')
          (wrap-tictoc** form fn-id' meta''))
        (do (swap! stack into args-meta)
            form)))
    (do (swap! stack into (list nil))
        form)))

(defn postwalk-
  [f form]
  (let [stack (atom '())
        f' (partial f stack)]
    (postwalk f' form)))


(pprint (postwalk- walker '(let [n 5]
    (doseq [i (range n)]
      (println (str "Hello world! #" i))
      (println (str (- n (inc i)) " more to go" ))))))