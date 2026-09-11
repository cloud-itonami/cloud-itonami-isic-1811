(ns printing.store-test
  (:require [clojure.test :refer [deftest is testing]]
            [printing.store :as store]))

(deftest mem-store-creation
  (testing "Create empty store"
    (let [st (store/mem-store)]
      (is (some? st))
      (is (satisfies? store/Store st))))

  (testing "Create store with initial press lines"
    (let [press-lines {"press-001" {:id "press-001" :name "Line 1"}}
          st (store/mem-store {:initial-press-lines press-lines})]
      (is (some? st))
      (is (satisfies? store/Store st)))))

(deftest registered-press-line-retrieval
  (testing "Retrieve existing press line"
    (let [press-line {:id "press-001" :name "Line 1"}
          st (store/mem-store {:initial-press-lines {"press-001" press-line}})]
      (is (= press-line (store/registered-press-line st "press-001")))))

  (testing "Retrieve non-existent press line"
    (let [st (store/mem-store)]
      (is (nil? (store/registered-press-line st "no-such-line")))))

  (testing "nil press-line-id returns nil (never falls through to a default)"
    (let [st (store/mem-store {:initial-press-lines {"press-001" {:id "press-001"}}})]
      (is (nil? (store/registered-press-line st nil))))))

(deftest add-press-line-test
  (testing "Register a new press line"
    (let [st (store/mem-store)
          press-line-data {:id "press-002" :name "Line 2"}
          result (store/add-press-line st "press-002" press-line-data)]
      (is (= press-line-data result))
      (is (= press-line-data (store/registered-press-line st "press-002")))))

  (testing "Update an existing press line"
    (let [st (store/mem-store {:initial-press-lines {"press-001" {:id "press-001"}}})
          updated {:id "press-001" :name "Renamed Line"}
          result (store/add-press-line st "press-001" updated)]
      (is (= updated result))
      (is (= updated (store/registered-press-line st "press-001"))))))

(deftest quality-inspection-index-test
  (testing "No inspection on file returns nil"
    (let [st (store/mem-store)]
      (is (nil? (store/quality-inspection-status st "press-001" "run-1")))))

  (testing "Recording an inspection makes it retrievable"
    (let [st (store/mem-store)]
      (store/record-quality-inspection! st "press-001" "run-1" "pass")
      (is (= "pass" (store/quality-inspection-status st "press-001" "run-1")))))

  (testing "Re-recording upserts, not forks history"
    (let [st (store/mem-store)]
      (store/record-quality-inspection! st "press-001" "run-1" "fail")
      (store/record-quality-inspection! st "press-001" "run-1" "pass")
      (is (= "pass" (store/quality-inspection-status st "press-001" "run-1")))))

  (testing "Distinct press-line+run keys are independent"
    (let [st (store/mem-store)]
      (store/record-quality-inspection! st "press-001" "run-1" "pass")
      (store/record-quality-inspection! st "press-001" "run-2" "fail")
      (store/record-quality-inspection! st "press-002" "run-1" "conditional-pass")
      (is (= "pass" (store/quality-inspection-status st "press-001" "run-1")))
      (is (= "fail" (store/quality-inspection-status st "press-001" "run-2")))
      (is (= "conditional-pass" (store/quality-inspection-status st "press-002" "run-1"))))))

(deftest ledger-test
  (testing "New store has an empty ledger"
    (let [st (store/mem-store)]
      (is (empty? (store/ledger st)))))

  (testing "append-ledger! appends in order"
    (let [st (store/mem-store)]
      (store/append-ledger! st {:t :committed :op :log-production-record})
      (store/append-ledger! st {:t :governor-hold :op :release-print-run})
      (is (= 2 (count (store/ledger st))))
      (is (= :committed (:t (first (store/ledger st)))))
      (is (= :governor-hold (:t (second (store/ledger st))))))))
