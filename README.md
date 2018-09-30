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
[jp.nijohando/failable "0.4.0-SNAPSHOT"]
```

#### Clojure CLI / deps.edn

```clojure
jp.nijohando/failable {:mvn/version "0.4.0-SNAPSHOT"}
```

## Usage

Clojure

```clojure
(require '[jp.nijohando.failable :as f])
```

CloureScript

```clojure
(require '[jp.nijohando.failable :as f :include-macros true])
```

### Error as a value

function `fail` creates a failure object representing an error.

```clojure
(f/fail)
;=> #jp.nijohando.failable.Failure{}
```

Failure object also can be created by specifying the reason for falure.

```clojure
(f/fail :user-not-found)
;=> #jp.nijohando.failable.Failure{
;     :jp.nijohando.failable/reason :user-not-found}
```

The reason can be retrieved by function `reason`

```clojure
(def x (f/fail :user-not-found))
(f/reason x)
;=> :user-not-found
```

`@` ( `deref` ) is more simple way to get the reason.

```clojure
(def x (f/fail :user-not-found))
@x
;=> :user-not-found

```

Since failure object is expressed as a record of type `jp.nijohando.failable.Failure`, Any additional infomation can be associated to the object.

```clojure
(-> (f/fail :user-not-found)
    (assoc :user-id 1001))
;=> #jp.nijohando.failable.Failure{
;     :jp.nijohando.failable/reason :user-not-found,
;     :user-id 10001}
```

### Nested error context

Failure object can be created by wrapping the another one or an exception that caused it.

```clojure
(def origin (f/fail :origin))
(def wrapped (f/wrap origin :wrapped))
wrapped
;=> #jp.nijohando.failable.Failure{
;     :jp.nijohando.failable/reason :wrapped, 
;     :jp.nijohando.failable/cause #jp.nijohando.failable.Failure{
;                                    :jp.nijohando.failable/reason :origin}}
(f/cause wrapped)
;=> #jp.nijohando.failable.Failure{
;     :jp.nijohando.failable/reason :origin}
```

### Alternative to try catch syntax

function `do*` is similar to [do](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core/do), but it captures any exception thrown from the body forms and converts it into a failure object , and returns it.

```clojure
(f/do* (throw (ex-info "test" {})))
;=> #jp.nijohando.failable.Failure{
;     :jp.nijohando.failable/reason :jp.nijohando.failable/exception, 
;     :cause #error { ... }}
```


### Verify a value

function `succ?` / `fail?` checks whether the value is successful or failed.  
Only values that are the instance of Failure will be failed.

```clojure
(def x (f/fail))
(def y 1)

(f/fail? x)
;=> true
(f/succ? x)
;=> false
(f/fail? y)
;=> false
(f/succ? y)
;=> true
```

function `ensure` throws an exception if the value is failed, otherwise the value is just returned.

```clojure
(let [x (f/fail)]
  (f/ensure x))
;=> ExceptionInfo Failed to ensure value  clojure.core/ex-info (core.clj:4739)

```

```clojure
(let [x 1]
  (f/ensure x))
;=> 1
```


### Failable threading macros

There are 6 conditional threading macros that are similar to [some->](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core/some-%3E), [some->>](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core/some-%3E%3E), and [as->](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core/as-%3E) but the conditions are focused on whether the result is succeeded or failed.

| Macro        | Continuation condition | [exceptionproof](#exceptionproof) |
| :-           | :-:                    | :-:                               |
| `succ->`     | Successful             | -                                 |
| `succ->*`    | Successful             | ✓                                 |
| `fail->`     | Failed                 | -                                 |
| `fail->*`    | Failed                 | ✓                                 |
| `as-succ->`  | Successful             | -                                 |
| `as-succ->*` | Successful             | ✓                                 |
| `as-fail->`  | Failed                 | -                                 |
| `as-fial->*` | Failed                 | ✓                                 |

#### `succ->` `succ->*`

The threading is continued while the result is not failed.

```clojure
(f/succ-> 1
  (inc)
  (inc)
  (dec))
;=> 2
```

When expr is failed, the threading is stopped and failure object is returned.

```clojure
(f/succ-> 1
  (inc)
  ((fn [x] (f/fail)))
  (dec))
;=> #jp.nijohando.failable.Failure{}
```

#### `fail->` `fail->*`

These are the opposite of `succ->` `succ->*` that continue processing while the result is failed.

#### `as-succ->` `as-succ->*`

Like a `as->`, but the thrading is continued while the result is not failed.

```clojure
(f/as-succ-> 1 n
  (inc n)
  (inc n)
  (dec n))
;=> 2
```

When expr is failed, the threading is stopped and failure object is returned.

#### `as-fail->` `as-fail->*`

These are the opposite of `as-succ->` `as-succ->*` that continue processing while the result is failed.

### Failable let macros

There are 4 variants of let macros that are similar to [let](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core/let), 
but the binding evaluation is stopped if the bound value does not satisfy the condition.

| Macro   | Condition  | [exceptionproof](#exceptionproof) |
| :-      | :-         | :-:                               |
| `slet`  | Successful | -                                 |
| `slet*` | Successful | ✓                                 |
| `flet`  | Failed     | -                                 |
| `flet*` | Failed     | ✓                                 |

```clojure
(f/slet [_ (prn 1)
         _ (prn 2)
         x (f/fail)
         _ (prn 3)]
  (prn "Not reach here"))
;=> 1
;=> 2
;=> #jp.nijohando.failable.Failure{}
```

### Failable let-style-if macros

There are 4 let-style-if macros that are similar to [if-some](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core/if-some), but the conditions are focused on whether the result is succeeded or failed.

| Macro | Condition | [exceptionproof](#exceptionproof) |
| :-  | :-:  | :-: |
| `if-succ` | Successful  | - |
| `if-succ*` | Successful  | ✓ |
| `if-fail` | Failed | - |
| `if-fail*` | Failed | ✓ |


```clojure
(f/if-succ [x 1]
  :ok
  :ng)
;=> :ok
(f/if-succ [x (f/fail)]
  :ok
  :ng)
;=> :ng
```

```clojure
(f/if-fail [x 1]
  :ng
  :ok)
;=> :ok
(f/if-fail [x (f/fail)]
  :ng
  :ok)
;=> :ng
```

### Failable let-style-when macros

There are 4 let-style-when macros that are similar to [when-some](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core/when-some), but the conditions are focused on whether the result is succeeded or failed.

| Macro | Condition | [exceptionproof](#exceptionproof) |
| :-  | :-:  | :-: |
| `when-succ` | Successful  | - |
| `when-succ*` | Successful  | ✓ |
| `when-fail` | Failed | - |
| `when-fail*` | Failed | ✓ |

```clojure
(f/when-succ [x 1]
  :ok)
;=> :ok
(f/when-succ [x (f/fail)]
  :ok)
;=> nil

```

```clojure
(f/when-fail [x 1]
  :ng)
;=> nil
(f/when-fail [x (f/fail)]
  :ng)
;=> :ng

```

### Exceptionproof

A Function with an asterisk at end of the name is exceptionproof.  
(e.g `do*` ,`succ->*`, `fail->*` and so on)

Exceptionproof function captures any exception thrown internally, converts it to failure object, and handles it.

## License

© 2017-2018 nijohando  

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
