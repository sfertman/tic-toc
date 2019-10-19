# tic-toc
Clojure code profiler. Very basic for now but possibly offers a quick and dirty way for exploratory optimization of bottlenecks. Does not drill down into fns definitions; does a simple postwalk on the input forms<sup>*</sup> and dumps results in one big atom for analysis.

[![Clojars Project](https://img.shields.io/clojars/v/tic-toc.svg)](https://clojars.org/tic-toc)

## Profiling
Just wrap whatever with `profile` macro
```clojure
(require '[tic-toc.core :refer :all])

(profile
  (let [n 5000]
    (doseq [i (range n)]
      (println (str "Hello world! #" i))
      (println (str (- n (inc i)) " more to go" )))))

; =>
; Hello world! #0
; 4999 more to go
; Hello world! #1
; 4998 more to go
; Hello world! #2
; ...
; ...
; ...
; Hello world! #4998
; 1 more to go
; Hello world! #4999
; 0 more to go
; nil
```

# Run time summary
Metrics are accumulated in an atom `metrics`. Collects run time in nanoseconds for each unique function call. To see a quick summary use:
```clojure
(pprint (summary))

; =>
; {"clojure.core/range"
;  {:total-time 113784,
;   :args-time 0,
;   :fn-time 113784,
;   :calls 1,
;   :mean-total 113784.0,
;   :mean-args 0.0,
;   :mean-fn 113784.0},
;  "clojure.core/str"
;  {:total-time 73567489,
;   :args-time 27101634,
;   :fn-time 46465855,
;   :calls 10000,
;   :mean-total 7356.7489,
;   :mean-args 2710.1634,
;   :mean-fn 4646.5855},
; ...
; ...
; ...
;  "clojure.core/let"
;  {:total-time 1429307487,
;   :args-time 1429296603,
;   :fn-time 10884,
;   :calls 1,
;   :mean-total 1.429307487E9,
;   :mean-args 1.429296603E9,
;   :mean-fn 10884.0}}
; nil
```
However, for more complex expressions this may be too big to look at.
`top` will give the top 10 time consumers in the profiling session based on `:fn-time`:
```clojure
(pprint (top))

; =>
; (["clojure.core/println" 1328577766]
;  ["clojure.core/doseq" 50735764]
;  ["clojure.core/str" 46465855]
;  ["clojure.core/-" 13821717]
;  ["clojure.core/inc" 13279917]
;  ["clojure.core/range" 113784]
;  ["clojure.core/let" 10884])
; nil
```

## Raw data
Alternatively, you can access the raw data and pipe it to your favourite data analytics tool

```clojure
(pprint (take 5 @metrics))

; =>
; ({:fn-id :clojure.core/range__1763,
;   :time-ns 113784,
;   :arg-fns []}
;  {:fn-id :clojure.core/str__1764,
;   :time-ns 15726,
;   :arg-fns []}
;  {:fn-id :clojure.core/println__1765,
;   :time-ns 415226,
;   :arg-fns [:clojure.core/str__1764]}
;  {:fn-id :clojure.core/inc__1766,
;   :time-ns 19302,
;   :arg-fns []}
;  {:fn-id :clojure.core/-__1767,
;   :time-ns 37854,
;   :arg-fns [:clojure.core/inc__1766]})
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
To start a fresh profiling session simply reset the metrics atom using:
```clojure
(clear-session!)
```
