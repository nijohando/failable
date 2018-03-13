(ns jp.nijohando.failable-test-cljs
  (:require [cljs.test :as t :refer-macros [is deftest testing async]]
            [jp.nijohando.failable :as f :include-macros true]
            [cljs.core.async :refer [chan <! >! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(deftest flet-no-function-creation-boundary-test
  (testing "flet can be used in a go(...) block"
    (async done
           (go
             (f/flet [ch (chan 1)
                      _ (>! ch :d1)
                      r (<! ch)]
                     (is (= r :d1))
                     (done))))))

(deftest flet*-no-function-creation-boundary-test
  (testing "flet* can be used in a go(...) block"
    (async done
           (go
             (f/flet* [ch (chan 1)
                       _ (>! ch :d1)
                       r (<! ch)]
               (is (= r :d1))
               (done))))))

(deftest if-succ-no-function-creation-boundary-test
  (testing "if-succ can be use d in a go(...) block"
    (async done
           (go
             (let [ch (chan 1)
                   _ (>! ch 10)]
               (is (= 10 (f/if-succ [x (<! ch)]
                                    x))))
             (done)))))

(deftest if-succ*-no-function-creation-boundary-test
  (testing "if-succ* can be use d in a go(...) block"
    (async done
           (go
             (let [ch (chan 1)
                   _ (>! ch 10)]
               (is (= 10 (f/if-succ* [x (<! ch)]
                                     x))))
             (done)))))

(deftest if-fail-no-function-creation-boundary-test
  (testing "if-fail can be use d in a go(...) block"
    (async done
           (go
             (let [ch (chan 1)
                   _ (>! ch (f/fail :foo-error))]
               (is (= (f/if-fail [x (<! ch)]
                                 @x)
                      :foo-error)))
             (done)))))

(deftest if-fail*-no-function-creation-boundary-test
  (testing "if-fail* can be use d in a go(...) block"
    (async done
           (go
             (let [ch (chan 1)
                   _ (>! ch (f/fail :foo-error))]
               (is (= (f/if-fail* [x (<! ch)]
                                  @x)
                      :foo-error)))
             (done)))))

(deftest succ->-no-function-creation-boundary-test
  (testing "succ-> can be use d in a go(...) block"
    (async done
           (go
             (let [ch (chan 1)
                   _ (>! ch 10)]
               (is (= 8 (f/succ-> (<! ch)
                                  (dec)
                                  (dec)))))
             (done)))))


(deftest succ->*-no-function-creation-boundary-test
  (testing "succ->* can be use d in a go(...) block"
    (async done
           (go
             (let [ch (chan 1)
                   _ (>! ch 10)]
               (is (= 8 (f/succ->* (<! ch)
                                   (dec)
                                   (dec)))))
             (done)))))

(deftest succ->>-no-function-creation-boundary-test
  (testing "succ->> can be use d in a go(...) block"
    (async done
           (go
             (let [ch (chan 1)
                   _ (>! ch 10)]
               (is (= 8 (f/succ->> (<! ch)
                                   (dec)
                                   (dec)))))
             (done)))))

(deftest succ->>*-no-function-creation-boundary-test
  (testing "succ->>* can be use d in a go(...) block"
    (async done
           (go
             (let [ch (chan 1)
                   _ (>! ch 10)]
               (is (= 8 (f/succ->>* (<! ch)
                                    (dec)
                                    (dec)))))
             (done)))))
