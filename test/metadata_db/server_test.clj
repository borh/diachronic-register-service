(ns diachronic-register-service.server-test
  (:require
   [clojure.test :refer :all]
   [diachronic-register-service.server :refer :all]
   [plumbing.core :refer [assoc-when safe-get]]
   [schema.core :as s]
   [clj-http.client :as client]
   [cheshire.core :as cheshire]
   [diachronic-register-service.schemas :as schemas]))

(def +port+
  "A random port on which to host the service"
  6054)

(def +base-url+
  (format "http://localhost:%s/metadata-api/" +port+))

(defn http-post [url body]
  (client/post
   (str +base-url+ url)
   {:content-type :json
    :as :json
    :throw-exceptions false
    :body (cheshire/generate-string body)}))

(defn http-get [url & [qps]]
  (client/get
   (str +base-url+ url)
   (assoc-when
    {:as :json
     :throw-exceptions false}
    :query-params qps)))

(defn http-delete [url]
  (client/delete
   (str +base-url+ url)
   {:as :json
    :throw-exceptions false}))

(deftest diachronic-register-service-test
  (let [guestbook (atom {})
        resources {:test (atom 0)
                   :db [[{:document/category "衆議院\tその他\t予算委員会第八分科会",
                          :document/basename "OM34_00001",
                          :document/title "国会会議録",
                          :document/year 1986,
                          :document/subcorpus "OM",
                          :db/id {:part :db.part/user, :idx -1000046}}
                         {:paragraph/tags #{:quotation :speech :speaker},
                          :paragraph/sentences
                          [{:sentence/text "○住主査",
                            :sentence/words
                            ({:word/position 0,
                              :word/lemma "○",
                              :word/pos "補助記号",
                              :word/orth-base "○"}
                             {:word/position 1,
                              :word/lemma "住",
                              :word/pos "名詞",
                              :word/orth-base "住"}
                             {:word/position 2,
                              :word/lemma "主査",
                              :word/pos "名詞",
                              :word/orth-base "主査"})}],
                          :db/id {:part :db.part/user, :idx -1000047}}]]}

        word-entry {:orth-base "主査"
                    :pos "名詞"}

        client-word-entry {:lemma "主査"
                           :orth-base "主査"
                           :pos "名詞"
                           ;;:count 1
                           }

        server (start-api resources {:port +port+ :join? false})]
    (try
      (comment (testing "adding new entries"
                 (is (= client-john-entry
                        (:body (http-post "entries" john-entry))))

                 (is (= (assoc john-entry :index 1)
                        (safe-get @guestbook 1)))

                 (is (= client-jane-entry
                        (:body (http-post "entries" jane-entry))))))

      (testing "viewing existing entries"
        (is (= client-word-entry
               (:body (http-get "entries" (seq word-entry))))))

      (comment
        (testing "search for entries by name"
          (is (= [client-john-entry]
                 (:body (http-get "search" {:q "John"}))))

          (is (= #{client-john-entry client-jane-entry}
                 (set (:body (http-get "search" {:q "Doe"}))))))

        (testing "view an individual entry"
          (is (= client-john-entry
                 (:body (http-get "entries/1")))))

        (testing "entry updates are reflected in the resources"
          (let [updated-john-entry (update-in john-entry [:age] inc)]
            (is (= (:body schemas/ack)
                   (:body (http-post "entries/1" updated-john-entry))))

            (is (= updated-john-entry (dissoc (get @guestbook 1) :index)))

            ;; update it back
            (is (= (:body schemas/ack) (:body (http-post "entries/1" john-entry))))))

        (testing "deleting entries"
          (assert (get @guestbook 1))
          (is (= (:body schemas/ack)
                 (:body (http-delete "entries/1"))))

          (is (nil? (get @guestbook 1))))

        (testing "accessing non-existant entries"
          (is (= 404 (:status (http-post "entries/1234567" john-entry))))

          (is (= 404 (:status (http-delete "entries/123456"))))))

      (finally (server)))))
