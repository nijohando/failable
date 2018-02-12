(ns prj.test.cases
  (:require #?(:clj  [clojure.test :as t :refer  [run-tests]]
               :cljs [cljs.test :as t :refer-macros [run-tests]])
            [jp.nijohando.failable-test]
            #?(:cljs [jp.nijohando.failable-test-cljs])))

(defn run-all
  []
  (run-tests #?@(:clj  ['jp.nijohando.failable-test]
                 :cljs ['jp.nijohando.failable-test 'jp.nijohando.failable-test-cljs])))

