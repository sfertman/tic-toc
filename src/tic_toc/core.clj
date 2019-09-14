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
  ([timer callback] (callback timers timer)))

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

(defmacro fn-name [f] `(-> ~f resolve symbol keyword)) ;; <-- fn-name should prolly be a function

(defn wrap-tictoc
  [form]
  `(let [fn-key# (-> '~form first fn-name)]
    (tic! fn-key#)
    (let [ret# ~form]
      (toc! fn-key#)
      ret#)))

(defmacro prof-1 [form] (wrap-tictoc form))

(defmacro prof-n [& forms] `(do ~@(map wrap-tictoc forms)))

(def mx1 (comp pprint macroexpand-1))

;; this macro must return something to indicate that f is not something that can be invoked
;; nil perhaps? -- TODO later


(prof-1 (byte-array [1 2 3 4]))



(prof-n (+ 2 3) (- 7 6) (str "42" "abc"))
