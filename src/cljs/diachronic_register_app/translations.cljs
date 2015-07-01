(ns diachronic-register-app.translations
  (:require [taoensso.timbre :as log]))

(defn ja->en [k1 k2 v]
  (if (or (re-seq #"\d+" v) (re-seq #"[a-zA-Z\s]+" v))
    v
    (let [translation-map
          {"document" {"media" {"新書" "New book"
                                "文庫" "Paperback"
                                "単行本" "Independent volume"
                                "全集・双書" "Complete work of/series"}
                       "subcorpus" {"OM" "Minutes of the Diet (OM)"
                                    "PM" "Magazines (PM)"
                                    "PB" "Books (PB)"
                                    "LB" "Books (LB)"
                                    "OB" "Books (OB)"}}}]
      (when-not (get-in translation-map [k1 k2 v])
        (log/info "Missing translation for" k1 k2 v))
      (get-in
       translation-map
       [k1 k2 v]
       v))))
