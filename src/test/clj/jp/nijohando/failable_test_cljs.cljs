(ns jp.nijohando.failable-test-cljs
  (:require [cljs.test :as t :refer-macros [run-tests is are deftest testing async]]
            [jp.nijohando.failable :refer-macros [flet f-> f->>]]
            [cljs.core.async :refer [chan <! >! close! take!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(deftest flet-test
  (testing "flet can be used in a go(...) block"
    (async end
      (go
        (flet [ch (chan 1)
               _ (>! ch :d1)
               r (<! ch)]
          (is (= r :d1))
          (end))))))


(deftest f->-test
  (testing "f-> can be use d in a go(...) block"
    (async end
      (go
        (let [ch (chan 1)
              _ (>! ch 10)]
          (is (= 8 (f-> (<! ch)
                        (dec)
                        (dec)))))
        (end)))))

(deftest f->>-test
  (testing "f->> can be use d in a go(...) block"
    (async end
      (go
        (let [ch (chan 1)
              _ (>! ch 10)]
          (is (= 8 (f->> (<! ch)
                           (dec)
                           (dec)))))
        (end)))))

