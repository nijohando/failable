# Failable

[![Clojars Project](https://img.shields.io/clojars/v/jp.nijohando/failable.svg)](https://clojars.org/jp.nijohando/failable)
[![CircleCI](https://circleci.com/gh/nijohando/failable.svg?style=shield)](https://circleci.com/gh/nijohando/failable)

Error handling helpers in Clojure(Script)

## Rationale

Main motivations for writing this library are:

* Managing errors as values insted of try/catch syntax
* Available on both Clojure and ClojureScript
* No function creation boundaries (Can be used in core.async go block)

## Installation

#### Ligningen / Boot

```clojure
[jp.nijohando/failable "0.2.0"]
```

#### Clojure CLI / deps.edn

```clojure
jp.nijohando/failable {:mvn/version "0.2.0"}
```

## Usage 

```clojure
(require '[jp.nijohando.failable :as f])
```

### Create an error as a value

`fail` creates an error.

With reason specified by keyword.

```clojure
(f/fail ::not-found)
;;=> #jp.nijohando.failable.Failed[{:status :ready, :val :user/not-found} 0x18205c08]
```

Additional error attributes can be passed as a map.

```clojure
(f/fail ::validation {:name :constraints [[:max-length 255] [:invalid-chars ["&" "$"]]]
                      :age  :constraints [[:min 18]]})
;;=> #jp.nijohando.failable.Failed[{:status :ready, :val :user/validation} 0x1f711438]
```

Wrap other failure(or exception)

```clojure
(let [cause (f/fail ::image-format-error)]
  (f/fail ::upload-image-error cause))
;;=> #jp.nijohando.failable.Failed[{:status :ready, :val :user/upload-image-error} 0x3138f7b9]
```

Wrap other failure(or exception) with error attributes.

```clojure
(let [cause (f/fail ::image-format-error)]
  (f/fail ::upload-image-error {:image "my-profile01.webp"} cause))
;;=> #jp.nijohando.failable.Failed[{:status :ready, :val :user/upload-image-error} 0x9eeff19]
```

### Verify a value

`succ?`, `fail?` checks whether the value is successful or failed.  
Only values that satisfy all the following conditions will be failed.

* Implements `jp.nijohando.failable.Failable` Protocol
* Result of the method `-fail?` is true

```clojure
(def x (f/fail ::duplicate-email-address))
(def y 1)

(f/fail? x)
;;=> true
(f/succ? x)
;;=> false
(f/fail? y)
;;=> false
(f/succ? y)
;;=> true
```


An exception is thrown if the value is an error, otherwise the value is just returned.

```clojure
(let [x (f/fail ::sql-error)]
  (f/fensure x))
;;=> ExceptionInfo :user/sql-error  clojure.core/ex-info (core.clj:4739)
```

```clojure
(let [x 1]
  (f/fensure x))
;;=> 1
```


### Inspect an error

Get reason.

```clojure
(def x (f/fail ::bad-request))

@x
;;=> :user/bad-request
(f/reason x)
;;=> :user/bad-request
```

Get error attributes.

```clojure
(def x (f/fail ::bad-request {:account-id "A00000000001"}))
(f/attrs x)
;;=> {:account-id "A00000000001"}
```

Get throwable

```
(def x (f/fail ::bad-request))
(f/throwable x)
;;=> #error {
 :cause ":user/bad-request"
 :data {:reason :user/bad-request}
 :via
 [{:type clojure.lang.ExceptionInfo
   :message ":user/bad-request"
   :data {:reason :user/bad-request}
   :at [clojure.core$ex_info invokeStatic "core.clj" 4739]}]
 :trace
 [[clojure.core$ex_info invokeStatic "core.clj" 4739]
  [clojure.core$ex_info invoke "core.clj" 4739]
  [jp.nijohando.failable$fail invokeStatic "failable.cljc" 78]
```

### Failable threading macro

`succ->`, `succ->>` are similar to `some->`, `some->>`, but they continue processing while the result is not failed.

```clojure
(f/succ-> 1
          (inc)
          (inc)
          (dec))
;;=> 2
(f/succ-> 1
          (inc)
          ((fn [x] (f/fail ::stop-the-threading)))
          (dec))
;;=> #jp.nijohando.failable.Failed[{:status :ready, :val :user/stop-the-threading} 0x3bc1711d]
```

```clojure
(f/succ->> 1
           (+ 1)
           (+ 1)
           (- 1))
;;=> -2
(f/succ->> 1
           (+ 1)
           ((fn [x] (f/fail ::stop-the-threading)))
           (- 1))
;;=> #jp.nijohando.failable.Failed[{:status :ready, :val :user/stop-the-threading} 0x1baba519]
```

There are similar macros `succ->*`, `succ->>*` that are **exceptionproof**.  
These macros capture an exception in a thread and convert it into an error.

```clojure
;; No exceptionproof
(f/succ-> 1
          (inc)
          ((fn [x] (throw (ex-info "example" {}))))
          (dec))
;;=> ExceptionInfo example  clojure.core/ex-info (core.clj:4739)
```

```clojure
;; Exceptionproof
(f/succ->* 1
           (inc)
           ((fn [x] (throw (ex-info "example" {}))))
           (dec))
;;=> #jp.nijohando.failable.Failed[{:status :ready, :val :jp.nijohando.failable/exceptionproof} 0x2d088638]
```

### Failable test macro


`if-succ`, `if-fail` are similar to `if-some`, but they test the value is successful or failed.

```clojure
(if-succ [x (f/fail :account-not-found)]
  :ok
  :ng)
;;=> :ng
```

```clojure
(if-fail [x (f/fail :account-not-found)]
  :ng
  :ok)
;;=> :ng
```

There are similar macros `if-succ*`, `if-fail*` that are **exceptionproof**.  
These macros capture an exception in bindings and body and convert it into an error.

`when-succ`, `when-fail` are similar to `when-some`, but they test the value is successful or failed.

```clojure
(when-succ [x 1]
  :ok)
;;=> :ok
(when-succ [x (f/fail :account-not-found)]
  :ok)
;;=> nil

```

```clojure
(when-fail [x 1]
  :ng)
;;=> nil
(when-fail [x (f/fail :account-not-found)]
  :ng)
;;=> :ng

```

There are similar macros `when-succ*`, `when-fail*` that are **exceptionproof**.  
These macros capture an exception in bindings and body and convert it into an error.


### Failable let macro

`flet` is similar to `let`, but stop the evaluation if an error is bound.

```clojure
(f/flet [i (do (println "1") 1)
         j (do (println "2") 2)
         k (f/fail ::stop-the-evaluation)
         l (do (println "3") 3)]
  (println "Not reach here"))
;;=> 1
;;=> 2
;;=> #jp.nijohando.failable.Failed[{:status :ready, :val :user/stop-the-evaluation} 0x5f7f672e]
```

There is similar macro `flet*` that is **exceptionproof**.  
This macro captures an exception in bindings and body and convert it into an error.


## License

© 2017-2018 nijohando  

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
