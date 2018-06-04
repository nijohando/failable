(ns prj.user
  (:require [jp.nijohando.failable :as f]
            [jp.nijohando.prj.test :as prj-test]
            [prj.cljs]))

(defn test-clj
  []
  (prj-test/run-tests 'jp.nijohando.failable-test))

(defn test-cljs
  []
  (prj.cljs/test-cljs))

(defn repl-cljs
  []
  (prj.cljs/repl-cljs))
