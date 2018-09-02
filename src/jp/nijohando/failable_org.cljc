(ns jp.nijohando.failable-org)

(defprotocol Failable
  (-fail? [this]))

(defprotocol Failure
  (-reason [this])
  (-throwable [this])
  (-attrs [this]))

(deftype Failed [reason throwable]
  Failable
  (-fail? [this] true)
  Failure
  (-reason [this] reason)
  (-throwable [this] throwable)
  (-attrs [this] (ex-data throwable))
  #?@(:clj [clojure.lang.IDeref
            (deref [_] reason)]
      :cljs [cljs.core/IDeref
             (-deref [_] reason)]))

(defn- failure?
  [x]
  (satisfies? Failure x))

(defn throwable?
  [x]
  (instance? #?(:clj Throwable :cljs js/Error) x))

(defn fail?
  [x]
  (and (satisfies? Failable x) (-fail? x)))

(defn succ?
  [x]
  (not (fail? x)))

(defn reason
  [failure]
  (when (failure? failure)
    (-reason failure)))

(defn throwable
  [failure]
  (when (failure? failure)
    (-throwable failure)))

(defn attrs
  [failure]
  (when (failure? failure)
    (-attrs failure)))

(defn fthrow
  [failure-or-throwable]
  (some-> (if (throwable? failure-or-throwable)
            failure-or-throwable
            (throwable failure-or-throwable))
          (throw))
  (throw (#?(:clj IllegalArgumentException. :cljs js/Error) "Only failure or throwable can be thrown")))

(defn fail
  ([]
   (fail ::unknown))
  ([reason]
   (fail reason nil nil))
  ([reason attrs-or-cause]
   (cond
     (nil? attrs-or-cause) (fail reason nil nil)
     (map? attrs-or-cause) (fail reason attrs-or-cause nil)
     (throwable? attrs-or-cause) (fail reason nil attrs-or-cause)
     (fail? attrs-or-cause) (fail reason nil attrs-or-cause)))
  ([reason attrs cause]
   (let [msg (str reason)
         attrs* (merge (or attrs {})
                       {:reason reason})]
     (->> (cond
            (nil? cause) (ex-info msg attrs*)
            (throwable? cause) (ex-info msg attrs* cause)
            (fail? cause) (ex-info msg attrs* (throwable cause)))
          (Failed. reason)))))

(defn fensure
  [x]
  (if (failure? x)
    (fthrow (fail ::failed x))
    x))

(defmacro if-cljs
  [then else]
  (if (boolean (:ns &env)) then else))

(defmacro fdo
  [& forms]
  `(if-cljs
    (try
      ~@forms
      (catch js/Error e#
        (fail ::exceptionproof e#)))
    (try ~@forms
      (catch Exception e#
        (fail ::exceptionproof e#)))))

(defmacro flet
  [bindings & body]
  (->> (reverse (partition 2 bindings))
       (reduce (fn [acc [l r]]
                 `(let [x# ~r
                        ~l x#]
                    (if (fail? x#)
                      x#
                      ~acc)))
               `(do ~@body))))

(defmacro flet*
  [bindings & body]
  (let [bindings* (->> (partition 2 bindings)
                       (map (fn [[l r]]
                              [l `(fdo ~r)]))
                       (apply concat))]
    `(flet [~@bindings*]
       (fdo ~@body))))

(defmacro succ->
  [expr & body]
  (let [g (gensym)
        steps (map (fn [step]
                     `(if (fail? ~g)
                        ~g
                        (-> ~g
                            ~step)))
                   body)]
    `(let [~g ~expr
           ~@(interleave (repeat g) (butlast steps))]
       ~(if (empty? steps)
          g
          (last steps)))))


(defmacro succ->*
  [expr & body]
  (let [g (gensym)
        steps (map (fn [step]
                     `(if (fail? ~g)
                        ~g
                        (-> ~g
                            ~step)))
                   body)]
    `(fdo
      (let [~g ~expr
            ~@(interleave (repeat g) (butlast steps))]
        ~(if (empty? steps)
           g
           (last steps))))))

(defmacro succ->>
  [expr & body]
  (let [g (gensym)
        steps (map (fn [step]
                     `(if (fail? ~g)
                        ~g
                        (->> ~g
                             ~step)))
                   body)]
    `(let [~g ~expr
           ~@(interleave (repeat g) (butlast steps))]
       ~(if (empty? steps)
          g
          (last steps)))))



(defmacro succ->>*
  [expr & body]
  (let [g (gensym)
        steps (map (fn [step]
                     `(if (fail? ~g)
                        ~g
                        (->> ~g
                             ~step)))
                   body)]
    `(fdo
      (let [~g ~expr
            ~@(interleave (repeat g) (butlast steps))]
        ~(if (empty? steps)
           g
           (last steps))))))


(defmacro if-succ
  ([bindings then]
   `(if-succ ~bindings ~then nil))
  ([bindings then else]
   (let [l (bindings 0)
         r (bindings 1)
         g (gensym)]
     `(let [~g ~r
            ~l ~g]
        (if (succ? ~g)
          ~then
          ~(when (some? else)
             else))))))

(defmacro if-succ*
  ([bindings then]
   `(if-succ* ~bindings ~then nil))
  ([bindings then else]
   (let [l (bindings 0)
         r (bindings 1)
         g (gensym)]
     `(let [~g (fdo ~r)
            ~l ~g]
        (fdo
          (if (succ? ~g)
            ~then
            ~(when (some? else)
               else)))))))

(defmacro if-fail
  ([bindings then]
   `(if-fail ~bindings ~then nil))
  ([bindings then else]
   (let [l (bindings 0)
         r (bindings 1)
         g (gensym)]
     `(let [~g ~r
            ~l ~g]
        (if (fail? ~g)
          ~then
          ~(when (some? else)
             else))))))

(defmacro if-fail*
  ([bindings then]
   `(if-fail* ~bindings ~then nil))
  ([bindings then else]
   (let [l (bindings 0)
         r (bindings 1)
         g (gensym)]
     `(let [~g (fdo ~r)
            ~l ~g]
        (fdo
          (if (fail? ~g)
            ~then
            ~(when (some? else)
               else)))))))

(defmacro when-succ
  [bindings then]
  `(if-succ ~bindings ~then))

(defmacro when-fail
  [bindings then]
  `(if-fail ~bindings ~then))

(defmacro when-succ*
  [bindings then]
  `(if-succ* ~bindings ~then))

(defmacro when-fail*
  [bindings then]
  `(if-fail* ~bindings ~then))
