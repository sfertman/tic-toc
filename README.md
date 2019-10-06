# tic-toc
Clojure code profiler. Very basic for now but possibly offers a quick and dirty way for exploratory optimization of bottlenecks. Does not drill down into fns definitions; does a simple postwalk on the input forms.<sup>*</sup>

## Profiling
Just wrap whatever with `profile` macro
```clojure
(require '[tic-toc.core :refer [profile]])

(profile
  (let [n 5]
    (doseq [i (range n)]
      (println (str "Hello world! #" i))
      (println (str (- n (inc i)) " more to go" )))))

; =>
; Hello world! #0
; 4 more to go
; Hello world! #1
; 3 more to go
; Hello world! #2
; 2 more to go
; Hello world! #3
; 1 more to go
; Hello world! #4
; 0 more to go
; nil
```

# Run time summary
Metrics are accumulated in an atom `metrics`. Collects run time in nanoseconds for each unique function call. To see a quick summary use:
```clojure
(require '[tic-toc.core :refer [summary]])

(pprint (summary))

; =>
; {"clojure.core/range" {:tot 112870.0, :calls 1.0, :avg 112870.0},
;  "clojure.core/str" {:tot 161584.0, :calls 10.0, :avg 16158.4},
;  "clojure.core/println" {:tot 2742432.0, :calls 10.0, :avg 274243.2},
;  "clojure.core/inc" {:tot 22670.0, :calls 5.0, :avg 4534.0},
;  "clojure.core/-" {:tot 82665.0, :calls 5.0, :avg 16533.0},
;  "clojure.core/doseq" {:tot 3342629.0, :calls 1.0, :avg 3342629.0},
;  "clojure.core/let" {:tot 3380295.0, :calls 1.0, :avg 3380295.0}}
; nil
```

## Raw data
Alternatively, you can access the raw data and pipe it to your favourite data analytics tool

```clojure
(require '[tic-toc.core :refer [metrics]])

(pprint @merics)

; =>
; [{:fn-id :clojure.core/range__1639, :time-ns 96980}
;  {:fn-id :clojure.core/str__1640, :time-ns 66228}
;  {:fn-id :clojure.core/println__1641, :time-ns 1506678}
;  {:fn-id :clojure.core/-__1642, :time-ns 49048}
;  ...
;  ...
;  ...
;  {:fn-id :clojure.core/doseq__1645, :time-ns 8885768}
;  {:fn-id :clojure.core/let__1646, :time-ns 8933516}]
; nil
```

<sup>*</sup> Note that `profile` supports multiple inputs. Will return the value of the last input form. Metrics will be collected in the same atom for all forms and all subsequent calls to `profile`.

```clojure
(profile
  (+ 42 43 44 45 46)
  (- 46 45 44 43 42)
  (* 1 2 3 4 5 6 7)
  (/ 46 45 44 43 42))
```
To start a fresh profiling session simply reset the metrics atom:
```clojure
(reset! metrics [])
```