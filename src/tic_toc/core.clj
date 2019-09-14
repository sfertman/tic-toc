(ns tic-toc.core)

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
  ([timer] (swap! dissoc timers timer)))
  
;;; metrics
(defonce metrics (atom []))

(defn collect! [m] (swap! metrics conj m))
(defn collect!* [fnk tns] (collect! {:fn-name fnk :time-ns tns}))

(defn toc! ;; <-- using toc callback to clean timers up and collect metrics
  [timer]
  (toc
    timer
    (fn [t T]
      (let [dt (toc t)]
        (clear! t)
        (collect!* t dt)
        dt))))
        
        
(defn wrap-tictoc
  [form]
  `(let [fn-key# (-> form first fn-name)]
    (tic! fn-key#)
    (let [ret# ~form]
      (toc! fn-key#)
      ret#)))
      
(defmacro prof-1 [form] (wrap-tictoc form))

(defmacro prof-n [forms] `(do ~@(map wrap-tictoc forms)))

(comment """
Could atually collect array of maps; sort of a column oriented db like cassandra
each 'log' entry map may consist of
  - fn-name  (mebbe even inputs and outputs?),
  - caller
  - execution time

""")

(def mx1 (comp pprint macroexpand-1))

(defmacro fn-name [f] `(-> ~f resolve symbol keyword)) ;; <-- fn-name should prolly be a function
;; this macro must return something to indicate that f is not something that can be invoked
;; nil perhaps? -- TODO later

(defmacro prof-1
  [form]
  `(let [fn-key# (fn-name '~(first form))] ;; <-- this works both with macro and fn
    (tic! fn-key#)
    (let [ret# ~form]
      (collect! {:fn-name fn-key# :time-ns (toc fn-key#)})
      ret#)))

(prof-1 (byte-array [1 2 3 4]))


(defmacro prof-n [& forms] 
  `(do 
    ~@(map (fn [form#] 
            (let [fn-key# (-> form# first fn-name)]
              (tic! fn-key#)
              (let [ret# form#]
                (collect! {:fn-name fn-key# :time-ns (toc fn-key#)})
                ret#)) )
            forms)))
;; ^ THIS works for profiling a bunch of forms

(prof-n (+ 2 3) (- 7 6) (str "42" "abc"))
