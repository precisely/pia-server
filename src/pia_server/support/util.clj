(ns pia-server.support.util)


(defn remove-nil-values [m]
  (into {} (filter #(let [[_ v] %] v) m)))

(defn nilable [p o] (or (nil? o) (p o)))

(defn ^{:arglists '[[m f & kvs] [m & kvs]]}
  assoc-if
  "Associates key value pairs with map for selected key value pairs.
  The second argument is a binary predicate (fn [k v] ...) which returns true
  to indicate that the k/v pair should be added. If omitted, a default predicate
  is used which returns v"
  [m & args]
  (let [maybe-fn (first args)
        [f kvs] (if (fn? maybe-fn)
                  [maybe-fn (rest args)]
                  [(fn [_ v] v) args])]
    (letfn [(internal-assoc-if [m kvs]
              (if (empty? kvs)
                m
                (let [[k v & rest-kvs] kvs]
                  (if (f k v)
                    (recur (assoc m k v) rest-kvs)
                    (recur m rest-kvs)))))]
      (internal-assoc-if m kvs))))
