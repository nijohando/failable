(ns jp.nijohando.failable-test
  (:refer-clojure :exclude [ensure])
  (:require [clojure.test :as t :refer [run-tests is deftest testing]]
            [jp.nijohando.failable :as f :include-macros true]))

(deftest fail
  (testing "Failure can be created"
    (let [x (f/fail)]
      (is (f/fail? x))
      (is (nil? (f/reason x)))))
  (testing "Failure can be created with reason"
    (let [x (f/fail ::test)]
      (is (f/fail? x))
      (is (= ::test (f/reason x)))))
  (testing "Failure can be associated with any extra information"
    (let [msg "this is test"
          x (-> (f/fail)
                (assoc :msg msg))]
      (is (f/fail? x))
      (is (= msg (:msg x))))))

(deftest reason
  (testing "Reason can be retrieved from faulure"
    (is (= :test (f/reason (f/fail :test))))
    (is (nil? (f/reason (f/fail)))))
  (testing "Reason can also be retrieved by deref"
    (is (= :test @(f/fail :test)))
    (is (nil? @(f/fail)))))

(deftest wrap-and-cause
  (testing "Failure can be created by wrapping the cause"
    (let [x (f/fail)
          y (f/wrap x :test)]
      (is (f/fail? y))
      (is (= :test @y))
      (is (= x (f/cause y))))))

(deftest ensure
  (testing "Value is just returned if it's not failure"
    (is (= :ok (f/ensure :ok)))
    (is (= true (f/ensure true)))
    (is (= false (f/ensure false)))
    (is (nil? (f/ensure nil))))
  (testing "Exception is thrown if value is failure"
    (let [x (f/fail)]
      (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"^Failed to ensure value" (f/ensure x))))))

(deftest do*
  (testing "Forms are evaluated and the last value is returned"
    (let [x (atom [])]
      (is (= (f/do*
               (swap! x conj :d1)
               (swap! x conj :d2)) [:d1 :d2]))))
  (testing "Exception is converted into failure, then returned"
    (let [ex (ex-info "test" {})
          x (f/do* (throw ex))]
      (is (f/fail? x))
      (is (= ex (f/cause x))))))

(deftest slet
  (testing "Return value"
    (testing "Literal value of the last form is returned"
      (is (= 1 (f/slet [_ nil] 1))))
    (testing "Failure value of the last form is returned"
      (is (= :test @(f/slet [_ nil] (f/fail :test)))))
    (testing "Bound value of the last form is returned"
      (is (= 1 (f/slet [a 1] a))))
    (testing "Result of the expression on the last form is returned"
      (is (= 2 (f/slet [a 1] (inc a)))))
    (testing "Bound failure value is just returned"
      (let [x (f/fail)
            y (f/slet [_ x] 1)]
        (is (f/fail? y))
        (is (= x y))))
    (testing "Exception on bindings is not captured"
      (is (thrown-with-msg?
           #?(:clj Exception :cljs js/Error) #"^test$"
           (f/slet [_ (throw (ex-info "test" {}))]
             nil))))
    (testing "Exception on body is not captured"
      (is (thrown-with-msg?
           #?(:clj Exception :cljs js/Error) #"^test$"
           (f/slet [_ 1]
             (throw (ex-info "test" {})))))))
  (testing "Discontinuation"
    (testing "Binding evaluation is stopped by failure"
      (let [mark (atom [])
            x (f/slet [a (do (swap! mark conj :step1)
                             1)
                       b (do (swap! mark conj :step2)
                             (f/fail ::test))
                       c (do (swap! mark conj :step3)
                             3)]
                (+ a b))]
        (is (f/fail? x))
        (is (= [:step1 :step2] @mark))
        (is (= ::test @x)))))
  (testing "Destructing"
    (testing "Binding form can be destructured"
      (is (= 1 (f/slet [{:keys [a]} {:a 1}] a)))
      (is (nil? (f/slet [{:keys [b]} {:a 1}] b)))
      (is (nil? (f/slet [{:keys [a]} nil] a))))
    (testing "Destructured value can be refered from other binding form"
      (is (= 2 (f/slet [{:keys [a]} {:a 1}
                        b (inc a)]
                 b))))
    (testing "Binding evaluation is stopped by failure"
      (let [mark (atom [])
            x (f/slet [{:keys [a]} (do (swap! mark conj :step1)
                                       {:a 1})
                       {:keys [b]} (do (swap! mark conj :step2)
                                       (f/fail ::test))
                       {:keys [c]} (do (swap! mark conj :step3)
                                       {:c 3})]
                (+ a b))]
        (is (f/fail? x))
        (is (= [:step1 :step2] @mark))
        (is (= ::test @x))))))

(deftest slet*
  (testing "Return value"
    (testing "Literal value of the last form is returned"
      (is (= 1 (f/slet* [_ nil] 1))))
    (testing "Failure value of the last form is returned"
      (let [x (f/fail)]
        (is (= x (f/slet* [_ nil] x)))))
    (testing "Bound value of the last form is returned"
      (is (= 1 (f/slet* [a 1] a))))
    (testing "Result of the expression on the last form is returned"
      (is (= 2 (f/slet* [a 1] (inc a)))))
    (testing "Bound failure value is just returned"
      (let [x (f/fail "error1")
            y (f/slet* [_ x] 1)]
        (is (f/fail? y))
        (is (= x y))))
    (testing "Exception on bindings is captured and returned as failure"
      (let [ex (ex-info "test" {})
            x (f/slet* [_ (throw ex)]
                nil)]
        (is (f/fail? x))
        (is (= ::f/exception @x))
        (is (= ex (f/cause x)))))
    (testing "Exception on body is captured and returned as failure"
      (let [ex (ex-info "test" {})
            x (f/slet* [_ 1] (throw ex))]
        (is (f/fail? x))
        (is (= ::f/exception @x))
        (is (= ex (f/cause x))))))
  (testing "Discontinuation"
    (testing "Binding evaluation is stopped by failure"
      (let [mark (atom [])
            x (f/slet* [a (do (swap! mark conj :step1)
                              1)
                        b (do (swap! mark conj :step2)
                              (f/fail ::test))
                        c (do (swap! mark conj :step3)
                              3)]
                (+ a b))]
        (is (f/fail? x))
        (is (= [:step1 :step2] @mark))
        (is (= ::test @x)))))
  (testing "Destructing"
    (testing "Binding form can be destructured"
      (is (= 1 (f/slet* [{:keys [a]} {:a 1}] a)))
      (is (nil? (f/slet* [{:keys [b]} {:a 1}] b)))
      (is (nil? (f/slet* [{:keys [a]} nil] a))))
    (testing "Destructured value can be refered from other binding form"
      (is (= 2 (f/slet* [{:keys [a]} {:a 1}
                         b (inc a)]
                 b))))
    (testing "Binding evaluation is stopped by failure"
      (let [mark (atom [])
            x (f/slet* [{:keys [a]} (do (swap! mark conj :step1)
                                        {:a 1})
                        {:keys [b]} (do (swap! mark conj :step2)
                                        (f/fail ::test))
                        {:keys [c]} (do (swap! mark conj :step3)
                                        {:c 3})]
                (+ a b))]
        (is (f/fail? x))
        (is (= [:step1 :step2] @mark))
        (is (= ::test @x))))))

(deftest flet
  (testing "Return value"
    (testing "Literal value of the last form is returned"
      (is (= 1 (f/flet [_ (f/fail)] 1))))
    (testing "Failure value of the last form is returned"
      (is (= :test @(f/flet [_ (f/fail)] (f/fail :test)))))
    (testing "Bound value of the last form is returned"
      (is (= :test @(f/flet [a (f/fail :test)] a))))
    (testing "Result of the expression on the last form is returned"
      (let [x (f/flet [a (f/fail :test)] (assoc a :msg "hello"))]
        (is (= :test @x))
        (is (= "hello" (:msg x)))))
    (testing "Bound succeeded value is just returned"
      (let [x (f/flet [_ 1] 2)]
        (is (f/succ? x))
        (is (= 1 x))))
    (testing "Exception on bindings is not captured"
      (is (thrown-with-msg?
           #?(:clj Exception :cljs js/Error) #"^test$"
           (f/flet [_ (throw (ex-info "test" {}))]
             nil))))
    (testing "Exception on body is not captured"
      (is (thrown-with-msg?
           #?(:clj Exception :cljs js/Error) #"^test$"
           (f/flet [_ (f/fail)]
             (throw (ex-info "test" {})))))))
  (testing "Discontinuation"
    (testing "Binding evaluation is stopped by success"
      (let [mark (atom [])
            x (f/flet [a (do (swap! mark conj :step1)
                             (f/fail))
                       b (do (swap! mark conj :step2)
                             1)
                       c (do (swap! mark conj :step3)
                             2)]
                (+ a b))]
        (is (f/succ? x))
        (is (= [:step1 :step2] @mark))
        (is (= 1 x)))))
  (testing "Destructing"
    (testing "Binding form can be destructured"
      (is (= 1 (f/flet [{:keys [a]} (-> (f/fail)
                                        (assoc :a 1))] a)))
      (is (nil? (f/flet [{:keys [b]} (-> (f/fail)
                                         (assoc :a 1))] b)))
      (is (nil? (f/flet [{:keys [a]} nil] 1))))
    (testing "Destructured value can be refered from other binding form"
      (let [x (f/flet [{:keys [cnt]} (-> (f/fail)
                                         (assoc :cnt 1))
                       a (-> (f/fail)
                             (assoc :cnt (inc cnt)))]
                a)]
        (is (= 2 (:cnt x)))))
    (testing "Binding evaluation is stopped by success"
      (let [mark (atom [])
            x (f/flet [{:keys [a]} (do (swap! mark conj :step1)
                                       (f/fail))
                       {:keys [b]} (do (swap! mark conj :step2)
                                       {:b 1})
                       {:keys [c]} (do (swap! mark conj :step3)
                                       {:c 2})]
                (+ b c))]
        (is (f/succ? x))
        (is (= [:step1 :step2] @mark))
        (is (= {:b 1} x))))))

(deftest flet*
  (testing "Return value"
    (testing "Literal value of the last form is returned"
      (is (= 1 (f/flet* [_ (f/fail)] 1))))
    (testing "Failure value of the last form is returned"
      (is (= :test @(f/flet* [_ (f/fail)] (f/fail :test)))))
    (testing "Bound value of the last form is returned"
      (is (= :test @(f/flet* [a (f/fail :test)] a))))
    (testing "Result of the expression on the last form is returned"
      (let [x (f/flet* [a (f/fail :test)] (assoc a :msg "hello"))]
        (is (= :test @x))
        (is (= "hello" (:msg x)))))
    (testing "Bound succeeded value is just returned"
      (let [x (f/flet* [_ 1] 2)]
        (is (f/succ? x))
        (is (= 1 x))))
    (testing "Exception on bindings is converted into failure"
      (let [ex (ex-info "test" {})
            x (f/flet* [a (throw ex)
                        b (f/wrap a :test)]
                b)]
        (is (f/fail? x))
        (is (= :test @x))
        (let [y (f/cause x)]
          (is (= ::f/exception @y))
          (is (= ex (f/cause y)))))))
  (testing "Discontinuation"
    (testing "Binding evaluation is stopped by success"
      (let [mark (atom [])
            x (f/flet* [a (do (swap! mark conj :step1)
                             (f/fail))
                       b (do (swap! mark conj :step2)
                             1)
                       c (do (swap! mark conj :step3)
                             2)]
                (+ a b))]
        (is (f/succ? x))
        (is (= [:step1 :step2] @mark))
        (is (= 1 x)))))
  (testing "Destructing"
    (testing "Binding form can be destructured"
      (is (= 1 (f/flet* [{:keys [a]} (-> (f/fail)
                                        (assoc :a 1))] a)))
      (is (nil? (f/flet* [{:keys [b]} (-> (f/fail)
                                         (assoc :a 1))] b)))
      (is (nil? (f/flet* [{:keys [a]} nil] 1))))
    (testing "Destructured value can be refered from other binding form"
      (let [x (f/flet* [{:keys [cnt]} (-> (f/fail)
                                         (assoc :cnt 1))
                       a (-> (f/fail)
                             (assoc :cnt (inc cnt)))]
                a)]
        (is (= 2 (:cnt x)))))
    (testing "Binding evaluation is stopped by success"
      (let [mark (atom [])
            x (f/flet* [{:keys [a]} (do (swap! mark conj :step1)
                                       (f/fail))
                       {:keys [b]} (do (swap! mark conj :step2)
                                       {:b 1})
                       {:keys [c]} (do (swap! mark conj :step3)
                                       {:c 2})]
                (+ b c))]
        (is (f/succ? x))
        (is (= [:step1 :step2] @mark))
        (is (= {:b 1} x))))))

(deftest succ->
  (testing "Continuation"
    (testing "Threading evaluation is continued until succeeded"
      (is (= "0" (f/succ->
                  1
                  (- 2)
                  (+ 1)
                  (str))))))
  (testing "Discontinuation"
    (testing "Threading evaluation is stopped by failure"
      (let [a (atom [])
            x (f/succ->
               1
               ((fn [n]
                  (swap! a conj :a)
                  (inc n)))
               ((fn [n]
                  (swap! a conj :b)
                  (inc n)))
               ((fn [n]
                  (f/fail ::test)))
               ((fn [n]
                  (swap! a conj :c)
                  (inc n)))
               (inc))]
        (is (f/fail? x))
        (is (= @x ::test))
        (is (= @a [:a :b]))))
    (testing "Threading evaluation is interrupted by exception"
      (is (thrown-with-msg?
           #?(:clj Exception :cljs js/Error) #"^test$"
           (f/succ->
            1
            (inc)
            ((fn [x] (throw (ex-info "test" {}))))))))))

(deftest succ->*
  (testing "Continuation"
    (testing "Threading evaluation is continued until scceeded"
      (is (= "0" (f/succ->*
                  1
                  (- 2)
                  (+ 1)
                  (str)))))
    (testing "Discontinuation"
      (testing "Threading evaluation is stopped by failure"
        (let [a (atom [])
              x (f/succ->*
                 1
                 ((fn [n]
                    (swap! a conj :a)
                    (inc n)))
                 ((fn [n]
                    (swap! a conj :b)
                    (inc n)))
                 ((fn [n]
                    (f/fail ::test)))
                 ((fn [n]
                    (swap! a conj :c)
                    (inc n)))
                 (inc))]
          (is (f/fail? x))
          (is (= ::test @x))
          (is (= [:a :b] @a))))
      (testing "Exception is captured and returned as failure"
        (let [ex (ex-info "test" {})
              a (atom [])
              x (f/succ->*
                 1
                 ((fn [n]
                    (swap! a conj :a)
                    (inc n)))
                 ((fn [n]
                    (swap! a conj :b)
                    (inc n)))
                 ((fn [n]
                    (throw ex)))
                 ((fn [n]
                    (swap! a conj :c)
                    (inc n)))
                 (inc))]
          (is (f/fail? x))
          (is (= [:a :b] @a))
          (is (= ::f/exception @x))
          (is (= ex (f/cause x))))))))

(deftest succ->>
  (testing "Continuation"
    (testing "Threading evaluation is continued until succeeded"
      (is (= "2" (f/succ->>
                  1
                  (- 2)
                  (+ 1)
                  (str))))))
  (testing "Discontinuation"
    (testing "Threading evaluation is stopped by failure"
      (let [a (atom [])
            x (f/succ->>
               1
               ((fn [n]
                  (swap! a conj :a)
                  (inc n)))
               ((fn [n]
                  (swap! a conj :b)
                  (inc n)))
               ((fn [n]
                  (f/fail ::test)))
               ((fn [n]
                  (swap! a conj :c)
                  (inc n)))
               (inc))]
        (is (f/fail? x))
        (is (= @x ::test))
        (is (= @a [:a :b]))))
    (testing "Threading evaluation is interrupted by exception"
      (is (thrown-with-msg?
           #?(:clj Exception :cljs js/Error) #"^test$"
           (f/succ->>
            1
            (inc)
            ((fn [x] (throw (ex-info "test" {}))))))))))

(deftest succ->>*
  (testing "Continuation"
    (testing "Threading evaluation is continued until succeeded"
      (is (= "2" (f/succ->>*
                  1
                  (- 2)
                  (+ 1)
                  (str))))))
  (testing "Discontinuation"
    (testing "Threading evaluation is stopped by failure"
      (let [a (atom [])
            x (f/succ->>*
               1
               ((fn [n]
                  (swap! a conj :a)
                  (inc n)))
               ((fn [n]
                  (swap! a conj :b)
                  (inc n)))
               ((fn [n]
                  (f/fail ::test)))
               ((fn [n]
                  (swap! a conj :c)
                  (inc n)))
               (inc))]
        (is (f/fail? x))
        (is (= ::test @x))
        (is (= [:a :b] @a))))
    (testing "Exception is captured and returned as failure"
      (let [ex (ex-info "test" {})
            a (atom [])
            x (f/succ->>*
               1
               ((fn [n]
                  (swap! a conj :a)
                  (inc n)))
               ((fn [n]
                  (swap! a conj :b)
                  (inc n)))
               ((fn [n]
                  (throw ex)))
               ((fn [n]
                  (swap! a conj :c)
                  (inc n)))
               (inc))]
        (is (f/fail? x))
        (is (= [:a :b] @a))
        (is (= ::f/exception @x))
        (is (= ex (f/cause x)))))))

(deftest fail->
  (testing "Continuation"
    (testing "Threading evaluation is continued until failed"
      (let [x (f/fail->
               (f/fail ::test1)
               ((fn [x _]
                  (is (= ::test1 @x))
                  (f/fail ::test2)) :arg2)
               ((fn [x _]
                  (is (= ::test2 @x))
                  (f/fail ::test3)) :arg2)
               ((fn [x _] x) :arg2))]
        (is (f/fail? x))
        (is (= ::test3 @x)))))
  (testing "Discontinuation"
    (testing "Threading evaluation is stopped by success"
      (let [a (atom [])
            x (f/fail->
               (f/fail ::test1)
               ((fn [x]
                  (swap! a conj :a)
                  x))
               ((fn [x]
                  (swap! a conj :b)
                  x))
               ((fn [x]
                  :ok))
               ((fn [x]
                  (swap! a conj :c)
                  :ng))
               (inc))]
        (is (f/succ? x))
        (is (= x :ok))
        (is (= @a [:a :b]))))
    (testing "Threading evaluation is interrupted by exception"
      (is (thrown-with-msg?
           #?(:clj Exception :cljs js/Error) #"^test$"
           (f/fail->
            (f/fail)
            ((fn [x] (throw (ex-info "test" {}))))))))))

(deftest fail->*
  (testing "Continuation"
    (testing "Threading evaluation is continued until failed"
      (let [x (f/fail->*
               (f/fail ::test1)
               ((fn [x _]
                  (is (= ::test1 @x))
                  (f/fail ::test2)) :arg2)
               ((fn [x _]
                  (is (= ::test2 @x))
                  (f/fail ::test3)) :arg2)
               ((fn [x _] x) :arg2))]
        (is (f/fail? x))
        (is (= ::test3 @x)))))
  (testing "Discontinuation"
    (testing "Threading evaluation is stopped by success"
      (let [a (atom [])
            x (f/fail->*
               (f/fail)
               ((fn [x]
                  (swap! a conj :a)
                  x))
               ((fn [x]
                  (swap! a conj :b)
                  x))
               ((fn [x]
                  :ok))
               ((fn [x]
                  (swap! a conj :c)
                  :ng))
               (inc))]
        (is (f/succ? x))
        (is (= x :ok))
        (is (= @a [:a :b]))))
    (testing "Exception is captured and converted into failure"
      (let [ex (ex-info "test" {})
            a (atom [])
            x (f/fail->*
               (f/fail)
               ((fn [x]
                  (swap! a conj :a)
                  x))
               ((fn [x]
                  (throw ex))))]
        (is (f/fail? x))
        (is (= ::f/exception @x))
        (is (= ex (f/cause x)))
        (is (= [:a] @a))))))

(deftest fail->>
  (testing "Continuation"
    (testing "Threading evaluation is continued until failed"
      (let [x (f/fail->>
               (f/fail ::test1)
               ((fn [_ x]
                  (is (= ::test1 @x))
                  (f/fail ::test2)) :arg1)
               ((fn [_ x]
                  (is (= ::test2 @x))
                  (f/fail ::test3)) :arg1)
               ((fn [_ x] x) :arg1))]
        (is (f/fail? x))
        (is (= ::test3 @x)))))
  (testing "Discontinuation"
    (testing "Threading evaluation is stopped by success"
      (let [a (atom [])
            x (f/fail->>
               (f/fail ::test1)
               ((fn [x]
                  (swap! a conj :a)
                  x))
               ((fn [x]
                  (swap! a conj :b)
                  x))
               ((fn [x]
                  :ok))
               ((fn [x]
                  (swap! a conj :c)
                  :ng))
               (inc))]
        (is (f/succ? x))
        (is (= x :ok))
        (is (= @a [:a :b]))))
    (testing "Threading evaluation is interrupted by exception"
      (is (thrown-with-msg?
           #?(:clj Exception :cljs js/Error) #"^test$"
           (f/fail->>
            (f/fail)
            ((fn [x] (throw (ex-info "test" {}))))))))))

(deftest fail->>*
  (testing "Continuation"
    (testing "Threading evaluation is continued until failed"
      (let [x (f/fail->>*
               (f/fail ::test1)
               ((fn [_ x]
                  (is (= ::test1 @x))
                  (f/fail ::test2)) :arg1)
               ((fn [_ x]
                  (is (= ::test2 @x))
                  (f/fail ::test3)) :arg1)
               ((fn [_ x] x) :arg1))]
        (is (f/fail? x))
        (is (= ::test3 @x)))))
  (testing "Discontinuation"
    (testing "Threading evaluation is stopped by success"
      (let [a (atom [])
            x (f/fail->>*
               (f/fail)
               ((fn [x]
                  (swap! a conj :a)
                  x))
               ((fn [x]
                  (swap! a conj :b)
                  x))
               ((fn [x]
                  :ok))
               ((fn [x]
                  (swap! a conj :c)
                  :ng))
               (inc))]
        (is (f/succ? x))
        (is (= x :ok))
        (is (= @a [:a :b]))))
    (testing "Exception is captured and converted into failure"
      (let [ex (ex-info "test" {})
            a (atom [])
            x (f/fail->>*
               (f/fail)
               ((fn [x]
                  (swap! a conj :a)
                  x))
               ((fn [x]
                  (throw ex))))]
        (is (f/fail? x))
        (is (= ::f/exception @x))
        (is (= ex (f/cause x)))
        (is (= [:a] @a))))))

(deftest as-succ->
  (testing "Continuation"
    (testing "Threading evaluation is continued until succeeded"
      (is (= "0" (f/as-succ-> 1 n
                   (- n 2)
                   (+ 1 n)
                   (str n))))))
  (testing "Discontinuation"
    (testing "Threading evaluation is stopped by failure"
      (let [a (atom [])
            x (f/as-succ-> 1 n
                (do
                  (swap! a conj :a)
                  (inc n))
                (do
                  (swap! a conj :b)
                  (inc n))
                (f/fail ::test)
                (do
                  (swap! a conj :c)
                  (inc n))
                (inc n))]
        (is (f/fail? x))
        (is (= @x ::test))
        (is (= @a [:a :b]))))
    (testing "Threading evaluation is interrupted by exception"
      (is (thrown-with-msg?
           #?(:clj Exception :cljs js/Error) #"^test$"
           (f/as-succ-> 1 x
             (inc x)
             (#(throw (ex-info "test" {})))))))))

(deftest as-succ->*
  (testing "Continuation"
    (testing "Threading evaluation is continued until succeeded"
      (is (= "0" (f/as-succ->* 1 n
                   (- n 2)
                   (+ 1 n)
                   (str n))))))
  (testing "Discontinuation"
    (testing "Threading evaluation is stopped by failure (first expr)"
      (let [a (atom [])
            x (f/as-succ->* (f/fail ::test) n
                (do
                  (swap! a conj :a)
                  (inc n))
                (do
                  (swap! a conj :b)
                  (inc n))
                (do
                  (swap! a conj :c)
                  (inc n))
                (inc n))]
        (is (f/fail? x))
        (is (= @x ::test))
        (is (empty? @a))))
    (testing "Threading evaluation is stopped by failure"
      (let [a (atom [])
            x (f/as-succ->* 1 n
                (do
                  (swap! a conj :a)
                  (inc n))
                (do
                  (swap! a conj :b)
                  (inc n))
                (f/fail ::test)
                (do
                  (swap! a conj :c)
                  (inc n))
                (inc n))]
        (is (f/fail? x))
        (is (= @x ::test))
        (is (= @a [:a :b]))))
    (testing "Exception is captured and returned as failure (first expr)"
      (let [ex (ex-info "test" {})
            a (atom [])
            x (f/as-succ->* (throw ex) n
                (do
                  (swap! a conj :a)
                  (inc n))
                (do
                  (swap! a conj :b)
                  (inc n))
                (do
                  (swap! a conj :c)
                  (inc n))
                (inc n))]
        (is (f/fail? x))
        (is (empty? @a))
        (is (= ::f/exception @x))
        (is (= ex (f/cause x)))))
    (testing "Exception is captured and returned as failure"
      (let [ex (ex-info "test" {})
            a (atom [])
            x (f/as-succ->* 1 n
                (do
                  (swap! a conj :a)
                  (inc n))
                (do
                  (swap! a conj :b)
                  (inc n))
                ((throw ex))
                (do
                  (swap! a conj :c)
                  (inc n))
                (inc n))]
        (is (f/fail? x))
        (is (= [:a :b] @a))
        (is (= ::f/exception @x))
        (is (= ex (f/cause x)))))))

(deftest as-fail->
  (testing "Continuation"
    (testing "Threading evaluation is continued until failed"
      (is (= "abc" @(f/as-fail-> (f/fail "a") n
                      (f/fail (str (f/reason n) "b"))
                      (f/fail (str (f/reason n) "c")))))))
  (testing "Discontinuation"
    (testing "Threading evaluation is stopped by success"
      (let [a (atom [])
            x (f/as-fail-> (f/fail) n
                (do
                  (swap! a conj :a)
                  (f/fail))
                (do
                  (swap! a conj :b)
                  (f/fail))
                (inc 1)
                (do
                  (swap! a conj :c)
                  (f/fail)))]
        (is (f/succ? x))
        (is (= 2 x))
        (is (= @a [:a :b]))))
    (testing "Threading evaluation is interrupted by exception"
      (is (thrown-with-msg?
           #?(:clj Exception :cljs js/Error) #"^test$"
           (f/as-fail-> (f/fail) x
             (f/fail)
             (#(throw (ex-info "test" {})))))))))

(deftest as-fail->*
  (testing "Continuation"
    (testing "Threading evaluation is continued until failed"
      (is (= "abc" @(f/as-fail->* (f/fail "a") n
                      (f/fail (str (f/reason n) "b"))
                      (f/fail (str (f/reason n) "c")))))))
  (testing "Discontinuation"
    (testing "Threading evaluation is stopped by success"
      (let [a (atom [])
            x (f/as-fail->* (f/fail) n
                (do
                  (swap! a conj :a)
                  (f/fail))
                (do
                  (swap! a conj :b)
                  (f/fail))
                (inc 1)
                (do
                  (swap! a conj :c)
                  (f/fail)))]
        (is (f/succ? x))
        (is (= 2 x))
        (is (= @a [:a :b]))))
    (testing "Exception is captured and threading is continued (first expr)"
      (let [ex (ex-info "test" {})
            a (atom [])
            x (f/as-fail->* (throw ex) n
                (do
                  (is (f/fail? n))
                  (is (= ::f/exception (f/reason n)))
                  (is (= ex (f/cause n)))
                  (swap! a conj :a)
                  (f/fail))
                (do
                  (swap! a conj :b)
                  (f/fail))
                (do
                  (swap! a conj :c)
                  (f/fail))
                :end)]
        (is (f/succ? x))
        (is (= @a [:a :b :c]))
        (is (= :end x))))
    (testing "Exception is captured and threading is continued"
      (let [ex (ex-info "test" {})
            a (atom [])
            x (f/as-fail->* (f/fail) n
                (throw ex)
                (do
                  (is (f/fail? n))
                  (is (= ::f/exception (f/reason n)))
                  (is (= ex (f/cause n)))
                  (swap! a conj :a)
                  (f/fail))
                (do
                  (swap! a conj :b)
                  (f/fail))
                (do
                  (swap! a conj :c)
                  (f/fail))
                :end)]
        (is (f/succ? x))
        (is (= @a [:a :b :c]))
        (is (= :end x))))))

(deftest if-succ
  (testing "'then' form is evaluated if test is successful"
    (is (true? (f/if-succ [x 1]
                 (number? x)
                 false)))
    (is (true? (f/if-succ [x nil]
                 (nil? x)
                 false)))
    (is (true? (f/if-succ [x false]
                 (not x)
                 false))))
  (testing "'else' form is evaluated if test is failed"
    (is (= 3 (f/if-succ [x (f/fail)]
               (inc 1)
               (inc 2)))))
  (testing "nil is returned if test is failed and 'else' form is not defined"
    (is (nil? (f/if-succ [x (f/fail)]
                false))))
  (testing "Exception on bindings is not captured and thrown"
    (is (thrown-with-msg?
         #?(:clj Exception :cljs js/Error) #"^test$"
         (f/if-succ [x (throw (ex-info "test" {}))]
           false))))
  (testing "Exception on 'then' form is not captured and thrown"
    (is (thrown-with-msg?
         #?(:clj Exception :cljs js/Error) #"^test$"
         (f/if-succ [x 1]
           (throw (ex-info "test" {}))))))
  (testing "Exception on 'else' form is not captured and thrown"
    (is (thrown-with-msg?
         #?(:clj Exception :cljs js/Error) #"^test$"
         (f/if-succ [x (f/fail)]
           false
           (throw (ex-info "test" {})))))))

(deftest if-succ*
  (testing "'then' form is evaluated if test is successful"
    (is (true? (f/if-succ* [x 1]
                 (number? x)
                 false)))
    (is (true? (f/if-succ* [x nil]
                 (nil? x)
                 false)))
    (is (true? (f/if-succ* [x false]
                 (not x)
                 false))))
  (testing "'else' form is evaluated if test is failed"
    (is (= 3 (f/if-succ* [x (f/fail)]
               (inc 1)
               (inc 2)))))
  (testing "nil is returned if test is failed and 'else' form is not defined"
    (is (nil? (f/if-succ* [x (f/fail)]
                false))))
  (testing "Exception on 'then' form is captured and converted into failure"
    (let [ex (ex-info "test" {})
          x (f/if-succ* [x 1]
              (throw ex)
              false)]
      (is (f/fail? x))
      (is (= ::f/exception @x))
      (is (= ex (f/cause x)))))
  (testing "Exception on 'else' form is captured and converted into failure"
    (let [ex (ex-info "test" {})
          x (f/if-succ* [x (f/fail)]
              false
              (throw ex))]
      (is (f/fail? x))
      (is (= ::f/exception @x))
      (is (= ex (f/cause x))))))

(deftest if-fail
  (testing "'then' form is evaluated if test is failed"
    (is (true? (f/if-fail [x (f/fail)]
                 true
                 false))))
  (testing "'else' form is evaluated if test is successful"
    (is (= 3 (f/if-fail [x 1]
               (inc 1)
               (inc 2)))))
  (testing "nil is returned if test is successful and 'else' form is not defined"
    (is (nil? (f/if-fail [x 1]
                false))))
  (testing "Exception on bindings is not captured and thrown"
    (is (thrown-with-msg?
         #?(:clj Exception :cljs js/Error) #"^test$"
         (f/if-fail [x (throw (ex-info "test" {}))]
           false))))
  (testing "Exception on body is not captured and thrown"
    (is (thrown-with-msg?
         #?(:clj Exception :cljs js/Error) #"^test$"
         (f/if-fail [x (f/fail)]
           (throw (ex-info "test" {}))
           false)))))

(deftest if-fail*
  (testing "'then' form is evaluated if test is failed"
    (is (true? (f/if-fail* [x (f/fail)]
                 true
                 false))))
  (testing "'else' form is evaluated if test is successful"
    (is (= 3 (f/if-fail* [x 1] (inc 1)
                         (inc 2)))))
  (testing "nil is returned if test is successful and 'else' form is not defined"
    (is (nil? (f/if-fail* [x 1]
                false))))
  (testing "'then' form is evaluated if test throws exception"
    (is (true? (f/if-fail* [x (throw (ex-info "test" {}))]
                 true
                 false))))
  (testing "nil is returned if test is successful and 'else' form is not defined"
    (is (nil? (f/if-fail* [x 1]
                false)))))

(deftest when-succ
  (testing "'body' form is evaluated if test is successful"
    (is (true? (f/when-succ [x 1]
                 (number? x)))))
  (testing "'body' forms are evaluated if test is successful"
    (is (= 102 (let [a (atom 100)]
                 (f/when-succ [x 1]
                   (swap! a + x)
                   (swap! a inc)
                   @a)))))
  (testing "nil is returned if test is failed"
    (is (nil? (f/when-succ [x (f/fail)]
                false))))
  (testing "Exception on bindings is not captured and thrown"
    (is (thrown-with-msg?
         #?(:clj Exception :cljs js/Error) #"^test$"
         (f/when-succ [x (throw (ex-info "test" {}))]
           false))))
  (testing "Exception on body is not captured and thrown"
    (is (thrown-with-msg?
         #?(:clj Exception :cljs js/Error) #"^test$"
         (f/when-succ [x 1]
           (throw (ex-info "test" {})))))))

(deftest when-succ*
  (testing "'body' form is evaluated if test is successful"
    (is (true? (f/when-succ* [x 1]
                 (number? x)))))
  (testing "'body' forms are evaluated if test is successful"
    (is (= 102 (let [a (atom 100)]
                 (f/when-succ* [x 1]
                   (swap! a + x)
                   (swap! a inc)
                   @a)))))
  (testing "nil is returned if test is failed"
    (is (nil? (f/when-succ* [x (f/fail)]
                false))))
  (testing "nil is returned if test throws exception"
    (is (nil? (f/when-succ* [x (throw (ex-info "test" {}))]
                false))))
  (testing "Exception on body is captured and converted into failure"
    (let [ex (ex-info "test" {})
          x (f/when-succ* [x 1]
              (throw ex))]
      (is (f/fail? x))
      (is (= ::f/exception @x))
      (is (= ex (f/cause x))))))

(deftest when-fail
  (testing "'body' form is evaluated if test is failed"
    (is (true? (f/when-fail [x (f/fail)]
                 true))))
  (testing "'body' forms are evaluated if test is successful"
    (is (= 102 (let [a (atom 100)]
                 (f/when-fail [x (f/fail)]
                   (swap! a inc)
                   (swap! a inc)
                   @a)))))
  (testing "nil is returned if test is successful"
    (is (nil? (f/when-fail [x 1]
                false))))
  (testing "Exception on bindings is not captured and thrown"
    (is (thrown-with-msg?
         #?(:clj Exception :cljs js/Error) #"^test$"
         (f/when-fail [x (throw (ex-info "test" {}))]
           false))))
  (testing "Exception on body is not captured and thrown"
    (is (thrown-with-msg?
         #?(:clj Exception :cljs js/Error) #"^test$"
         (f/when-fail [x (f/fail)]
           (throw (ex-info "test" {})))))))

(deftest when-fail*
  (testing "'body' form is evaluated if test is failed"
    (is (true? (f/when-fail* [x (f/fail)]
                 true))))
  (testing "'body' forms are evaluated if test is successful"
    (is (= 102 (let [a (atom 100)]
                 (f/when-fail* [x (f/fail)]
                   (swap! a inc)
                   (swap! a inc)
                   @a)))))
  (testing "nil is returned if test is successful"
    (is (nil? (f/when-fail* [x 1]
                false))))
  (testing "Exception on test is converted into failure and 'body' form is evaluated"
    (is (true? (f/when-fail* [x (throw (ex-info "test" {}))]
                 true))))
  (testing "Exception on body is converted into failure"
    (let [ex (ex-info "test" {})
          x (f/when-fail* [x (f/fail)]
              (throw ex))]
      (is (f/fail? x))
      (is (= ::f/exception @x))
      (is (= ex (f/cause x))))))
