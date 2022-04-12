(ns pia-server.util)


(defn remove-nil-values [m]
  (into {} (filter #(let [[_ v] %] v) m)))

(defn nilable [p o] (or (nil? o) (p o)))

(defn ^{:arglists '[[map f & kvs] [map & kvs]]}
  assoc-if
  "Associates key value pairs with map for selected key value pairs.

  Args:
    f - A function (fn [k v] ...) which returns a boolean value to indicate if the k/v pair should be added.
        If omitted, a default function is used which returns v"
  [map & args]
  (let [maybe-fn (first args)
        [f kvs] (if (fn? maybe-fn)
                  [maybe-fn (rest args)]
                  [(fn [_ v] v) args])]
    (loop [map map
           kvs kvs]
      (if (empty? kvs) map
        (let [[k v & rest] kvs]
          (recur (if (f k v) (assoc map k v) map) rest))))))