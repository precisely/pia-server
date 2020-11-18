(ns pia-server.flows.cfsdemo
  (:require [longterm :refer :all]
            ))


(deflow foo-remote []
        (*> "hello")
        (let [value (<* :permit :foo :expires (-> 2 minutes from-now) :default "default-suspend-value")]
          (*> (str value " world!"))
          :result))

(deflow no-severe-fatigue []
        (*> "This is the flow where non-fatigued people end up"))

(deflow fatigue-iom-6m []
        (*> "This is the flow where people failing PEM go"))


(deflow welcome []
        (*> "Welcome to the Precisely app!")
        (*> "The purpose here is to track your symptoms and determine if you are developing long-term covid problems.")
        (*> "Specifically, the problems being tracked here can lead to Chronic Fatigue Syndrome (CFS)")
        (*> "You can assess your own risk for CFS by simply answering the next questions about how you feel.")
        (*> "It usually takes less than 10 minutes, or only seconds if you are showing no signs of CFS"))

(defn general-num-slider [min min-tag max max-tag text increment]
  (hash-map :type :num-slider
            :min min
            :min-tag min-tag
            :max max
            :max-tag max-tag
            :text text
            :increment increment)
  )

(deflow general []

        (*> "Now, let's chat about fatigue you've been feeling since last week.")
        (let [
              _ (*> "Is your motivation lower when you've been fatigued?",
                    {:type      :num-slider
                     :min       1
                     :min-tag   "Strongly disagree"
                     :max       7
                     :max-tag   "Strongly agree"
                     :text      "In the last 7 days"
                     :increment 1
                     :reply     "_"
                     })
              motivation (<*)


              ])
        (*> "Is your motivation lower when you've been fatigued?")
        (*> {:type      :num-slider
             :min       1
             :min-tag   "Strongly disagree"
             :max       7
             :max-tag   "Strongly agree"
             :text      "In the last 7 days"
             :increment 1
             :reply     "_"
             })
        (let [motivation (<*)]
          (*> "Does exercise bring on your fatigue?")
          (*> {:type      :num-slider
               :min       1
               :min-tag   "Strongly disagree"
               :max       7
               :max-tag   "Strongly agree"
               :text      "In the last 7 days"
               :increment 1
               :reply     "_"
               })
          (let [exercise (<*)]

        ;; If score >= 36, continue to next module, if not then group as "No severe fatigue"
        ;; Second question - what is the best clojure way to take the 9 answers and sum them?
        (if (< (+ q1 q2) 36)
          (no-severe-fatigue)
            (*> "It sounds like you have some serious fatigue issues."
             "This means there could be problems in your recovery that we need to look at more closely.")))
        )

(deflow iof []
        (*> "These next 9 questions will ask about specifically how your function in daily tasks are affected.")
        ;; This time question should go at the end of this section, as specified by IOM diagnostic flow
        ;; Although it may need to be its own section
        (*> "How long have you been feeling affected?")
        (*> {:type    :choices
             :choices ["a day or two", "1 week", "3 months", "6 months", "More"]
             :text    "Choose closest time that fits you"
             })
        (let [q1 (<* :expires (-> 2 minutes from-now) :default nil)]
          :result)
        )

(defn grader [f s]
  (f >= 2 and s >= 2))

(deflow pem []
        ;;Use a loop/recur to iterate through question data and then dont recur when you need to reach end conditions
        (*> "Now we will look at a symptom called Post-Exertional Malaise (PEM).")
        (*> "This is a type of fatigue that occurs after activity. These questions usually take less than a minute.")
        (*> "Considering how you've felt in the last 6 months, answer how often and how sever the following feelings were...")
        (*> "How often did you get a dead, heavy feeling after starting exercise?")
        ;; show this slider type to aneil
        (*> {:type  text-slider
             :tags  ["None of the time", "Rarely", "Half the time", "Most of the time", "All of the time"]
             :text  "In the last 6 months, I felt this"})
        (let [f1 (<* :default nil)])
        (*> "How severe was this dead, heavy feeling after starting exercise?")
        (*> {:type  text-slider
             :tags  ["not present", "mild", "moderate", "severe", "very severe"]
             :text  "It felt "})
        (let [s1 (<* :default nil)])
        (when (grader f1 s1)
          (do (*> "Thanks, from your answers so far it looks like you have significant PEM.")
           (*> "Let's move on to your sleep now. There are only 3 sections left, at most. Almost done!"))
          )

        ;;After all the questions in this section, if none have triggered the grader fn it goes into bucket
        (*> "Thanks for answering these questions about your PEM.")
        (*> "Your PEM so far is looking normal, and for now this is the end of the assessment.")
        (*> "")

        )


(deflow home []
        (welcome)
        (general)
        (iom)
        ;; How can you have a return value from a run be called here for bucket logic?
        )