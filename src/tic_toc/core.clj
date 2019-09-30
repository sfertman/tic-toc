(ns tic-toc.core
  (:require
    [clojure.pprint :refer [pprint]] ;; remove in release
    [clojure.walk :refer [walk postwalk]]
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

(defn inner-fn
  [form]
  (if (fn-call? form)
    (let [meta-data (get-meta form)]
      [(wrap-tictoc** form (fn-id form) meta-data ) meta-data])
    ;; ^^ we make a new fn-id in each function so they will be different!
    ;; we need to make an fn-id once and pass it to both wrap-tictoc* and get-meta
    [form nil]))
;; ^ applied on every argument form


(defn outer-fn
  [form-meta-pairs]
  (let [form (map first form-meta-pairs)
        meta-data (filter some? (map second form-meta-pairs))]
    (wrap-tictoc* form)
    (prn "form" form)
    (prn "meta-data" meta-data)))


(defn wrap-tictoc ;; <-- this guy now needs to walk the form with inner and outter defed above
  [form]
  (if (fn-call? form)
    (wrap-tictoc* form)
    form))

(defmacro profile [& forms] `(do ~@(map (partial postwalk wrap-tictoc) forms)))

(defn summary [] (mtr/summary metrics))




;;///////////////////////////////////////////////////////////
(defrecord FormWithMeta [form meta])
(defn fwm? [form] (instance? FormWithMeta form))
(defn- get-if-instance
  "Return k from m if m is instance of c; otherwise returns not-found or nil"
  ([c m k] (get-if-instance c m k nil))
  ([c m k not-found] (if (instance? c m) (get m k not-found) not-found)))
(defn get-if-fwm
  "Returns k (either :form or :meta) if fwm-maybe is FormWithMeta; otherwise returns fwm-maybe"
  [fwm-maybe k] (get-if-instance FormWithMeta fwm-maybe k fwm-maybe))



(defn fwm-list? [form] (and (list? form) (every? fwm? form))) ;; <-- todo: this should not exist

(def fwm- (FormWithMeta. 42 43))
(def eg-form
  '(let [n 5]
     (doseq [i (range n)]
      (println (str "Hello world! #" i))
      (println (str (- n (inc i)) (+ 42 24) " more to go" ))
      (println [12 13 14])
      (println {:a 1 :b 2 #_#_:c (+ 1 2)})
      (println fwm-))))

(defn unzip-fwms*
  [fwms]
  [(map :form fwms)
   (filter some? (map :meta fwms))])

(defn unzip-fmp*
  [form-meta-pairs]
  [(seq (map first form-meta-pairs))
   (seq (filter some? (map second form-meta-pairs)))])

(defn unzip-fmp
  [form-meta-pairs]
  (let [form (map first form-meta-pairs)
        meta* (filter some? (map second form-meta-pairs))]
    (if (empty? meta*)
      [form nil]
      [form meta*])))

(defn wtt ;; outer
  [form-meta-pairs]
  (let [[form args-meta] (unzip-fmp form-meta-pairs)]
    (if (fn-call? form)
      (let [fn-id* (fn-id form)]
        (wrap-tictoc** form fn-id* {:fn-id fn-id* :args-meta args-meta}))
      [form nil])))

(defn form-meta-pair
  [form]
  (if (fn-call? form)
    (let [fn-id (fn-id form)
          meta-data {:fn-id fn-id}]
      [(wrap-tictoc** form fn-id meta-data) meta-data]) ;; <-- wtt is already in wtt; this function has a different function
    [form nil]))

;;; GET RID OF walk! SHOULD ONLY HAVE ON FN THAT HANDLES ALL CASES WITH postwalk
;;; Look at the example in the readme and go from there
;;; actually after another look it seems that I need more sources of info about the form tree without transforming the tree itself; so neither of these solutions will work great; it is possible that clojure doesn't have anything built in for something like this so I'll have to build it from scratch....

;; perhaps postwalk with side effect that keeps the original form structure in an atom


[(+ 9 8 7) {:args [] :fn-id '+}] ;--> (wrap-tictoc** form '+ meta)

(defn form-walker
  [form]
  (if (fn-call? form)
    (walk form-meta-pair wtt form)
    form))

(defmacro profile2 [form] (postwalk form-walker form))

(pprint (walk form-meta-pair wtt '(+ 9 8 (- 7 6) (/ 6 5) 42 355)))


;; IMPORTANT NOTE: doc says "applies" but in reality it calls the fn
; clojure.walk/walk
; ([inner outer form])
;   Traverses form, an arbitrary data structure.  inner and outer are
;   functions.  ~~Applies~~Calls inner ~~to~~with each element of form, building up a
;   data structure of the same type, then ~~applies~~calls outer ~~to~~with the result.
;   Recognizes all Clojure data structures. Consumes seqs as with doall.


(defn inner
  [form]
  (prn "inner" form)
  (if (fn-call? form)
    `[(with-tictoc! ~form)
      {:fn-id ~(fn-id form)}]
    [form nil]))



; (defn walker
;   [form]
;   (cond
;     (fwm-list? form) ;; processing as outer
;       (let [[frm mta] (unzip-fwms* form)]
;         (FormWithMeta. frm {:args-fn-ids (map :fn-id mta)}))
;     (fn-call? form) ;; processing as inner fn-call
;       (FormWithMeta. (wrap-tictoc* form) {:fn-id (fn-id form)})
;     :else  ;; processing as inner thing we don't know how to deal with
;       (FormWithMeta. form nil)))



(defn scrape-meta ;; need to add all the cases
  [fwms]
  (prn "scraping" fwms)
  (cond
    (list? fwms)
      ;; NOTE: ^^ this spcial case is required for lists because if we regard
      ;; it as a simple collection it will conj elements and result will be
      ;; in reverse order
      (do (prn "as list")(FormWithMeta.
        (apply list (map #(get-if-fwm % :form) fwms))
        (filter some? (map #(get-if-fwm % :meta) fwms))))
    (instance? clojure.lang.IMapEntry fwms)
      (do (prn "as map-entry" ) (FormWithMeta.
        (clojure.lang.MapEntry/create (get-if-fwm (key fwms) :form) (get-if-fwm (val fwms) :form))
        (filter some? [(get-if-fwm (key fwms) :meta) (get-if-fwm (val fwms) :meta)])))

    #_#_(map? fwms) ;; Do not use map?, use MapEntry like clojure.walk/walk; map will be captured by coll? but map entry is a collection that needs to be walked also.
      (do (prn "as map")(FormWithMeta.
        (into (empty fwms) (map (fn [[k v]] [(get-if-fwm k :form ) (get-if-fwm v :form)]) fwms))
        (filter some? (reduce (fn [mta [k v]] (conj mta (get-if-fwm k :meta) (get-if-fwm v :meta))) '() fwms))))

    (coll? fwms)
      (do (prn "as coll") (FormWithMeta.
        (into (empty fwms) (map #(get-if-fwm % :form) fwms))
        (filter some? (map #(get-if-fwm % :meta) fwms))))
    :else ;; should never to happen
      (do (prn "oh boy! else has been tripped") fwms)))

#_(defn walk
  [inner outer form]
  (cond
    (list? form)
      (outer (apply list (map inner form)))
    (instance? clojure.lang.IMapEntry form)
      (outer (clojure.lang.MapEntry/create (inner (key form)) (inner (val form))))
    (seq? form)
      (outer (doall (map inner form)))
    (instance? clojure.lang.IRecord form)
      (outer (reduce (fn [r x] (conj r (inner x))) form form))
    (coll? form)
      (outer (into (empty form) (map inner form)))
    :else
      (outer form)))

#_(defn walker
  [form]
  (if (coll? form)
    (let [{form' :form meta' :meta} (scrape-meta form)]
      (prn "After scraping:")
      (prn "form" form')
      (prn "meta" meta')
      (if (fn-call? form')
        (let [fn-id' (fn-id form')]
          (prn "hurray! form is a function call")
          (FormWithMeta.
            `(--- ~form' ~fn-id' {:fn-id ~fn-id' :args-fn-ids ~meta'})
            #_(wrap-tictoc** form' fn-id' {:fn-id fn-id' :args-fn-ids meta'}) ;; fn-id of this fn is not needed; I'll keep it here just for debugging for now
            {:fn-id fn-id'}))
        (do (prn "not fn-call")(FormWithMeta. form' meta'))    ))
            ;; ^^ this is where fn-id actually useful
    (FormWithMeta. form nil)))


; (walker '(inc i))
; (walker '(- n (inc i)))
; (walker '(str (- n (inc i)) " more to go" (+ 42 x)))


(defn inner
  [stack form];; like
  (prn "inner form" form)
  (swap! stack conj nil))

;; GIANT TODO
;; this is the latest and greatest
;; it uses a stack to track meta and this does not modify the forms
;; it supports any clojure form that walk does simply because it doesn't
;; try to get smart and uses postwalk
;; ~~1. fix the damn logic here -- something went wrong deleting prn outputs~~
;; ~~2. plug the correct tictoc wrapper whatver it is~~
;; 3. this is very cool but meta dosn't propagate through more than one layer -- fix this and it's all done!
;; 4. organize this file and delete everything that's not needed
;; 5. test
;; 6. test

(defn walker
  [stack form]
  (if (coll? form)
    (let [c (count form)
          meta' {:args-fns (seq (filter some? (map :fn-id (take c @stack))))}]
      (prn "meta'" meta')
      (swap! stack #(drop c %))
      (if (fn-call? form)
        (let [fn-id' (fn-id form)
              meta'' (assoc meta' :fn-id fn-id')]
          (swap! stack conj meta'')
          (wrap-tictoc** form fn-id' meta''))
        (do (swap! stack conj meta')
            form)))
    (do (swap! stack conj nil)
        form)))


(defn postwalk*
  [inner outer form]
  (walk (partial postwalk* inner outer) outer form))

(defn postwalk-
  [inner outer form]
  (let [stack (atom '())
        inner' (partial inner stack)
        outer' (partial outer stack)]
    (postwalk* inner' outer' form)))

(defn postwalk-
  [f form]
  (let [stack (atom '())
        f' (partial f stack)]
    (postwalk f' form)))