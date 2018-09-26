(ns jp.nijohando.failable-test-cljs
  (:require [clojure.test :as t :refer-macros [is deftest testing async]]
            [jp.nijohando.failable :as f :include-macros true]
            [clojure.core.async :as ca :include-macros true]))

(deftest flet-no-function-creation-boundary-test
  (testing "flet can be used in a go(...) block"
    (async done
           (ca/go
             (f/flet [ch (ca/chan 1)
                      _ (ca/>! ch :d1)
                      r (ca/<! ch)]
                     (is (= r :d1))
                     (done))))))

(deftest flet*-no-function-creation-boundary-test
  (testing "flet* can be used in a go(...) block"
    (async done
           (ca/go
             (f/flet* [ch (ca/chan 1)
                       _ (ca/>! ch :d1)
                       r (ca/<! ch)]
               (is (= r :d1))
               (done))))))

(deftest if-succ-no-function-creation-boundary-test
  (testing "if-succ can be use d in a go(...) block"
    (async done
           (ca/go
             (let [ch (ca/chan 1)
                   _ (ca/>! ch 10)]
               (is (= 10 (f/if-succ [x (ca/<! ch)]
                                    x))))
             (done)))))

(deftest if-succ*-no-function-creation-boundary-test
  (testing "if-succ* can be use d in a go(...) block"
    (async done
           (ca/go
             (let [ch (ca/chan 1)
                   _ (ca/>! ch 10)]
               (is (= 10 (f/if-succ* [x (ca/<! ch)]
                                     x))))
             (done)))))

(deftest if-fail-no-function-creation-boundary-test
  (testing "if-fail can be use d in a go(...) block"
    (async done
           (ca/go
             (let [ch (ca/chan 1)
                   _ (ca/>! ch (f/fail :foo-error))]
               (is (= (f/if-fail [x (ca/<! ch)]
                                 @x)
                      :foo-error)))
             (done)))))

(deftest if-fail*-no-function-creation-boundary-test
  (testing "if-fail* can be use d in a go(...) block"
    (async done
           (ca/go
             (let [ch (ca/chan 1)
                   _ (ca/>! ch (f/fail :foo-error))]
               (is (= (f/if-fail* [x (ca/<! ch)]
                                  @x)
                      :foo-error)))
             (done)))))

(deftest succ->-no-function-creation-boundary-test
  (testing "succ-> can be use d in a go(...) block"
    (async done
           (ca/go
             (let [ch (ca/chan 1)
                   _ (ca/>! ch 10)]
               (is (= 8 (f/succ-> (ca/<! ch)
                                  (dec)
                                  (dec)))))
             (done)))))


(deftest succ->*-no-function-creation-boundary-test
  (testing "succ->* can be use d in a go(...) block"
    (async done
           (ca/go
             (let [ch (ca/chan 1)
                   _ (ca/>! ch 10)]
               (is (= 8 (f/succ->* (ca/<! ch)
                                   (dec)
                                   (dec)))))
             (done)))))

(deftest succ->>-no-function-creation-boundary-test
  (testing "succ->> can be use d in a go(...) block"
    (async done
           (ca/go
             (let [ch (ca/chan 1)
                   _ (ca/>! ch 10)]
               (is (= 8 (f/succ->> (ca/<! ch)
                                   (dec)
                                   (dec)))))
             (done)))))

(deftest succ->>*-no-function-creation-boundary-test
  (testing "succ->>* can be use d in a go(...) block"
    (async done
           (ca/go
             (let [ch (ca/chan 1)
                   _ (ca/>! ch 10)]
               (is (= 8 (f/succ->>* (ca/<! ch)
                                    (dec)
                                    (dec)))))
             (done)))))
