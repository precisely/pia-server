(ns pia-server.flows.components)

(defn num-slider [min min-tag max max-tag text increment]
  (hash-map :type :num-slider
            :min min
            :min-tag min-tag
            :max max
            :max-tag max-tag
            :text text
            :increment increment)
  )

(defn choices [choices text]
  (hash-map :type :multiple-choice
            :choices choices
            :text text))