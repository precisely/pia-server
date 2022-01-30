(ns pia-server.common.util)

(defn round
  "Round a double to the given precision (number of significant digits)"
  [precision d]
  (let [factor (Math/pow 10 precision)
        d      (float d)]
    (/ (Math/round (* d factor)) factor)))

(defn round-to-nearest-half [num]
  (/ (round 0 (* num 2)) 2.0))

(defmacro range-case [target & cases]
  "Compare the target against a set of ranges or constant values and return
   the first one that matches. If none match, and there exists a case with the
   value :else, return that target. Each range consists of a vector containing
   one of the following patterns:
     [upper-bound]                 if this is the first pattern, match any
                                   target <= upper-bound
                                   otherwise, match any target <= previous
                                   upper-bound and <= upper-bound
     [< upper-bound]               if this is the first pattern, match any
                                   target < upper-bound
                                   otherwise, match any target <= previous
                                   upper-bound and < upper-bound
     [lower-bound upper-bound]     match any target where lower-bound <= target
                                   and target <= upper-bound
     [< lower-bound upper-bound]   match any target where lower-bound < target
                                   and target <= upper-bound
     [lower-bound < upper-bound]   match any target where lower-bound <= target
                                   and target < upper-bound
     [< lower-bound < upper-bound] match any target where lower-bound < target
                                   and target < upper-bound
   Example:
     (range-case target
                 [0 < 1] :strongly-disagree
                 [< 2]     :disagree
                 [< 3]     :neutral
                 [< 4]     :agree
                 [5]       :strongly-agree
                 42          :the-answer
                 :else       :do-not-care)
   expands to
     (cond
       (and (<= 0 target) (< target 1)) :strongly-disagree
       (and (<= 1 target) (< target 2)) :disagree
       (and (<= 2 target) (< target 3)) :neutral
       (and (<= 3 target) (< target 4)) :agree
       (<= 4 target 5) :strongly-agree
       (= target 42) :the-answer
       :else :do-not-care)"
  (if (odd? (count cases))
    (throw (IllegalArgumentException. (str "no matching clause: "
                                           (first cases))))
    `(cond
      ~@(loop [cases cases ret [] previous-upper-bound nil]
          (cond
           (empty? cases)
           ret

           (= :else (first cases))
           (recur (drop 2 cases) (conj ret :else (second cases)) nil)

           (vector? (first cases))
           (let [condition (first cases)
                 clause (second cases)

                 [case-expr prev-upper-bound]
                 (let [length (count condition)]
                   (cond
                    (= length 1)
                    (let [upper-bound (first condition)]
                      [(if previous-upper-bound
                         `(and (<= ~previous-upper-bound ~target)
                               (<= ~target ~upper-bound))
                         `(<= ~target ~upper-bound))
                       upper-bound])

                    (= length 2)
                    (if (= '< (first condition))
                      (let [[_ upper-bound] condition]
                        [(if previous-upper-bound
                           `(and (<= ~previous-upper-bound ~target)
                                 (< ~target ~upper-bound))
                           `(< ~target ~upper-bound))
                         upper-bound])
                      (let [[lower-bound upper-bound] condition]
                        [`(and (<= ~lower-bound ~target)
                               (<= ~target ~upper-bound))
                         upper-bound]))

                    (= length 3)
                    (cond
                     (= '< (first condition))
                     (let [[_ lower-bound upper-bound] condition]
                       [`(and (< ~lower-bound ~target)
                              (<= ~target ~upper-bound))
                        upper-bound])

                     (= '< (second condition))
                     (let [[lower-bound _ upper-bound] condition]
                       [`(and (<= ~lower-bound ~target)
                              (< ~target ~upper-bound))
                        upper-bound])

                     :else
                     (throw (IllegalArgumentException. (str "unknown pattern: "
                                                            condition))))

                    (and (= length 4)
                         (= '< (first condition))
                         (= '< (nth condition 3)))
                    (let [[_ lower-bound _ upper-bound] condition]
                      [`(and (< ~lower-bound ~target) (< ~target ~upper-bound))
                       upper-bound])

                    :else
                    (throw (IllegalArgumentException. (str "unknown pattern: "
                                                           condition)))))]
             (recur (drop 2 cases)
                    (conj ret case-expr clause)
                    prev-upper-bound))

           :else
           (let [[condition clause]
                 `[(= ~target ~(first cases)) ~(second cases)]]
             (recur (drop 2 cases) (conj ret condition clause) nil)))))))