(ns prj.user
  (:require [jp.nijohando.failable :as f :include-macros true]
            [jp.nijohando.failable-test]
            [jp.nijohando.failable-test-cljs]
            [cljs.test :refer-macros [run-tests]]))

(defn test-cljs
  []
  (run-tests 'jp.nijohando.failable-test
             'jp.nijohando.failable-test-cljs))
