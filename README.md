# Failable

[![CircleCI](https://circleci.com/gh/nijohando/failable.svg?style=svg&circle-token=7f8a9c5dffdd84b9b187eb1cc4e256b228878bc9)](https://circleci.com/gh/nijohando/failable)

Failable is clojure / clojurescript library that provides error handling helpers without try / catch syntax.


## Usage

#### Avoid try / catch syntax

`ftry` just returns result of the form, but if exception occurs,
it is converted into a failure object and returned as a return value.

```clojure
repl.clj=> (require '[jp.nijohando.failable :refer [ftry]])
repl.clj=> (ftry (inc 1))
2
repl.clj=> (ftry (throw (Exception.)))
#object[jp.nijohando.failable.Failure 0x251953af {:status :ready, :val #error {
 :cause nil
 :via
 [{:type java.lang.Exception
   :message nil
...
```

#### Check whether success or failure

`success?` and `failure?` check whether success or failure.

```clojure
repl.clj=> (require '[jp.nijohando.failable :refer [ftry failure? success?]])
repl.clj=> (failure? (ftry (throw (Exception.))))
true
repl.clj=> (success? (ftry (inc 1)))
true
```

#### Create failure object

Failure object can be created explicitly from message, other failure object or throwable.

```clojure
repl.clj=> (require '[jp.nijohando.failable :refer [fail]])
repl.clj=> (fail "some error occurred")
#object[jp.nijohando.failable.Failure 0x130f7819 {:status :ready, :val #error {
 :cause "some error occurred"
 :data {}
 :via
 [{:type clojure.lang.ExceptionInfo
   :message "some error occurred"
...
```

#### Failable let macro

`flet` is failable let macro.  
When exception occurs or failure object is bound, the binding evaluation is stopped and failure object is just returned as a return value.

```clojure
repl.clj=> (require '[jp.nijohando.failable :refer [flet fail]])
repl.clj=> (flet [a (inc 1)
                  b (fail "some error occurred")
                  c (inc a)]
                    (inc c))
#object[jp.nijohando.failable.Failure 0x1cd13a82 {:status :ready, :val #error {
 :cause "some error occurred"
 :data {}
 :via
 [{:type clojure.lang.ExceptionInfo
   :message "some error occurred"
```

#### Failable arrow macros

`f->` , `f->>`  are failable arrow macros.  
When exception occurs or failure object is returned, threading is stopped and failure object is just returned as a return value.


```clojure
repl.clj=> (require '[jp.nijohando.failable :refer [f->]])
repl.clj=> (f-> 1
                (inc)
                ((fn [x] (fail "some error occurs")))
                (dec))
#object[jp.nijohando.failable.Failure 0x519fb022 {:status :ready, :val #error {
 :cause "some error occurs"
 :data {}
 :via
 [{:type clojure.lang.ExceptionInfo
   :message "some error occurs"
```


## Development notes

### REPL driven development

#### Clojure

To get an interactive development environment run:

```
lein repl
```

Execute unit tests.

```clojure
repl.clj=> (test/run)
```

#### Clojurescript

To get an interactive development environment run:

```
lein repl
```

```clojure
repl.clj=> (fig-start)
repl.clj=> (cljs-repl)
repl.clj=> (in-ns 'repl.cljs)
```

and open your browser at [localhost:3449](http://localhost:3449/).
This will auto compile and send all changes to the browser without the
need to reload. After the compilation process is complete, you will
get a Browser Connected REPL.

Execute unit tests.

```clojure
repl.cljs=> (test/run)
```

### Unit tests without repl

for clojure.

```
lein clj-test
```

for clojurescript.  
(nodejs is required)

```
lein cljs-test
```
