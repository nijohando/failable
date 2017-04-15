(ns jp.nijohando.failable-test
  (:require #?(:clj  [clojure.test :as t :refer [run-tests is are deftest testing]]
               :cljs [cljs.test :as t :refer-macros [run-tests is are deftest testing]])
            [jp.nijohando.failable :as f :include-macros true]))

(deftest throwable?-test
  (testing "Exception or js/Error must be true"
    (is (f/throwable? #? (:clj (Exception.) :cljs (js/Error.))))
    (is (f/throwable? #? (:clj (Error.) :cljs (js/Error.))))
    )
  (testing "Others are not be true"
    (let [not-throwable? (comp not f/throwable?)]
      (is (not-throwable? nil))
      (is (not-throwable? true))
      (is (not-throwable? false))
      (is (not-throwable? "foo")))))

(deftest failure?-test
  (testing "Failure must be true"
    (is (f/failure? (f/fail "foo"))))
  (testing "Others must not be false"
    (let [not-failure? (comp not f/failure?)]
      (is (not-failure? nil))
      (is (not-failure? true))
      (is (not-failure? false))
      (is (not-failure? "foo")))))

(deftest success?-test
  (testing "Failure must be false"
    (is (not (f/success? (f/fail "foo")))))
  (testing "Others must be true"
    (is (f/success? nil))
    (is (f/success? true))
    (is (f/success? false))
    (is (f/success? "foo"))))

(deftest when-success-test
  (testing "Body is evaluated and returned, if value is not failure"
    (is (= :foo (f/when-success nil :foo)))
    (is (= 2 (f/when-success nil (inc 1)))))
  (testing "Nil is returned, if value is failure"
    (is (nil? (f/when-success (f/fail "bar") :foo)))))

(deftest when-failure-test
  (testing "Body is evaluated and returned, if value is failure"
    (is (= :foo (f/when-failure (f/fail "bar") :foo))))
  (testing "Nil is returned, if value is not failure"
    (is (nil? (f/when-failure nil :foo)))
    (is (nil? (f/when-failure true :foo)))
    (is (nil? (f/when-failure false :foo)))
    (is (nil? (f/when-failure "bar" :foo)))))

(deftest ftry-test
  (testing "Forms are evaludated and returned value"
    (let [x (atom [])]
      (is (= [:d1 :d2] (f/ftry
                         (swap! x conj :d1)
                         (swap! x conj :d2)
                         @x)))))
  (testing "Exception is caputured and converted into failure"
    (let [f (f/ftry (throw #?(:clj (Exception.) :cljs (js/Error.))))]
      (is (f/failure? f)))))

(deftest flet-return-value-test
  (testing "Body value is returned"
    (is (= 1 (f/flet [_ nil] 1))))
  (testing "Binding form is returned"
    (is (= 1 (f/flet [a 1] a))))
  (testing "Body form is evaluated and returned"
    (is (= 2 (f/flet [a 1] (inc a)))))
  (testing "Failure is just returned"
    (let [x (f/flet [_ (f/fail "error1")] 1)]
      (is (f/failure? x))
      (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"^error1$" (throw @x)))))
  (testing "Exception on bindings is captured and returned as failure"
    (let [x (f/flet [_ (throw (ex-info "error1" {}))] nil)]
      (is (f/failure? x))
      (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"^error1$" (throw @x)))))
  (testing "Exception on body is captured and returned as failure"
    (let [x (f/flet [_ 1] (throw (ex-info "error1" {})))]
      (is (f/failure? x))
      (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"^error1$" (throw @x))))))

(deftest flet-discontinuation
  (testing "Binding evaluation is stopped by failure"
    (let [mark (atom [])
          r (f/flet [a (do (swap! mark conj :step1)
                           1)
                     b (do (swap! mark conj :step2)
                           (f/fail "error1"))
                     c (do (swap! mark conj :step3)
                           3)]
              (+ a b))]
      (is (f/failure? r))
      (is (= [:step1 :step2] @mark))
      (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"^error1$" (throw @r))))))

(deftest flet-destructing-test
  (testing "Binding form is returned"
    (is (= 1 (f/flet [{:keys [a]} {:a 1}] a))))
  (testing "Binding evaluation is stopped by failure"
    (let [mark (atom [])
          r (f/flet [{:keys [a]} (do (swap! mark conj :step1)
                           {:a 1})
                     {:keys [b]} (do (swap! mark conj :step2)
                                     (f/fail "error1"))
                     {:keys [c]} (do (swap! mark conj :step3)
                                     {:c 3})]
              (+ a b))]
      (is (f/failure? r))
      (is (= [:step1 :step2] @mark))
      (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"^error1$" (throw @r))))))


(deftest f->return-value-test
  (testing "Body forms are evaludated and returned"
    (is (= 2 (f/f-> 1
                    (inc)
                    (inc)
                    (dec)))))
  (testing "Failure is returned"
    (is (f/failure? (f/f-> 1
                           ((fn [x] (f/fail "error1")))
                           (inc)))))
  (testing "Exception is captured and returned as failure"
    (let [x (f/f-> 1
                   ((fn [x] (throw (ex-info "error1" {})))))]
      (is (f/failure? x))
      (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"^error1$" (throw @x))))))

(deftest f->discontinuation
  (testing "Threading evaluation is stopped by failure"
    (let [x (atom [])
          r (f/f-> 1
              ((fn [n]
                 (swap! x conj :a)
                 (inc n)))
              ((fn [n]
                 (swap! x conj :b)
                 (inc n)))
              ((fn [n]
                 (f/fail "error1")))
              ((fn [n]
                 (swap! x conj :c)
                 (inc n)))
              (inc))]
      (is (f/failure? r))
      (is (= [:a :b] @x)))))

(deftest f->>return-value
  (testing "Body forms are evaludated returned"
    (is (= 2 (f/f->> 1
                      (inc)
                      (inc)
                      (dec)))))
  (testing "Failure is returned"
    (is (f/failure? (f/f->> 1
                            ((fn [x] (f/fail "error1")))))))
  (testing "Exception is captured and returned as failure"
    (let [x (f/f->> 1
                    ((fn [x] (throw (ex-info "error1" {})))))]
      (is (f/failure? x))
      (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"^error1$" (throw @x))))))

(deftest f->>discontinuation
  (testing "Threading evaluation is stopped by failure"
    (let [x (atom [])
          r (f/f->> 1
              ((fn [n]
                 (swap! x conj :a)
                 (inc n)))
              ((fn [n]
                 (swap! x conj :b)
                 (inc n)))
              ((fn [n]
                 (f/fail "error1")))
              ((fn [n]
                 (swap! x conj :c)
                 (inc n)))
              (inc))]
      (is (f/failure? r))
      (is (= [:a :b] @x)))))

(deftest guard-test
  (testing "Success is passed through"
    (is (= nil (f/guard nil)))
    (is (= true (f/guard true)))
    (is (= false (f/guard false)))
    (is (= "foo" (f/guard "foo"))))
  (testing "Failure is blocked and thrown"
    (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"^error1$" (f/guard (f/fail "error0") "error1")))))

