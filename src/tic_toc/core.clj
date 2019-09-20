(ns tic-toc.core
  (:require
    [clojure.pprint :refer [pprint]]))

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

(defn fn-key [form] (-> form first resolve symbol keyword gensym))

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

(pprint (wrap-tictoc '(fnfn 0 8 fgfg)))

(defmacro prof-1 [form] (wrap-tictoc form))

(defmacro prof-n [& forms] `(do ~@(map wrap-tictoc forms)))

(def mx1 (comp pprint macroexpand-1))

;; this macro must return something to indicate that f is not something that can be invoked
;; nil perhaps? -- TODO later


; (prof-1 (byte-array [1 2 3 4]))

; (prof-n (+ 2 3) (- 7 6) (str "42" "abc"))


(def x '(+ 9 8))
(def y '(9 8 7))
(def z '(gh fnf spl))



