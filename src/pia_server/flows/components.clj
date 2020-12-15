(ns pia-server.flows.components)


;(defn num-slider [min min-tag max max-tag text increment]
;  (hash-map :type :num-slider
;            :min min
;            :min-tag min-tag
;            :max max
;            :max-tag max-tag
;            :text text
;            :increment increment)
;  )
;
;(defn choices [choices text]
;  (hash-map :type :multiple-choice
;            :choices choices
;            :text text))

;; The following components are build for SurveyJS

;; Rating type in surveyjs
(defn rating [name text min min-tag max max-tag]
      (hash-map :type "rating"
                :name name
                :title text
                :isRequired true
                :rateMin min
                :rateMax max
                :minRateDescription min-tag
                :maxRateDescription max-tag
        ))

;; RadioGroup question https://surveyjs.io/Examples/Library?id=questiontype-radiogroup&platform=jQuery&theme=modern
(defn radiogroup [name text colCount choices]
      (hash-map :type "radiogroup"
                :name name
                :title text
                :isRequired true
                :colCount colCount
                :choices choices
        ))
