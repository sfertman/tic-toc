# tic-toc
Code profiler for clojure in clojure. Very basic for now but possibly offers a quick and dirty way for exploratory optimization of bottlenecks. Does not drill down into fns definitions; does a simple postwalk on the input form.

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

## Accessing metrics
Metrics are accumulated in an atom `metrics`. Collects run time in nanoseconds for each unique function call.

```clojure
(require '[tic-toc.core :refer [metrics]])

(pprint @merics)

; =>
; [{:fn-name :clojure.core/range__1639, :time-ns 96980}
;  {:fn-name :clojure.core/str__1640, :time-ns 66228}
;  {:fn-name :clojure.core/println__1641, :time-ns 1506678}
;  {:fn-name :clojure.core/-__1642, :time-ns 49048}
;  ...
;  ...
;  ...
;  {:fn-name :clojure.core/doseq__1645, :time-ns 8885768}
;  {:fn-name :clojure.core/let__1646, :time-ns 8933516}]
; nil
```