(ns printing.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [printing.governor :as gov]
            [printing.store :as store]))

(defn- registered-store
  ([] (registered-store #{}))
  ([verified-print-specs]
   (store/mem-store
    {:initial-press-lines
     {"press-001" {:id "press-001" :name "Line 1 (Offset)"
                   :press-type "offset"
                   :verified-print-specs verified-print-specs}}})))

(deftest hard-violations-no-press-line-id
  (testing "Hard violation: missing press-line-id"
    (let [req {}
          prop {:op :log-production-record :effect :propose}
          s (store/mem-store)
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (seq (:violations verdict)))
      (is (some #(= :press-line-not-registered (:rule %)) (:violations verdict))))))

(deftest hard-violations-unregistered-press-line
  (testing "Hard violation: press-line-id present but not registered"
    (let [req {:press-line-id "press-001"}
          prop {:op :log-production-record :effect :propose}
          s (store/mem-store)
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :press-line-not-registered (:rule %)) (:violations verdict))))))

(deftest hard-violations-effect-not-propose
  (testing "Hard violation: effect is not :propose"
    (let [s (registered-store)
          req {:press-line-id "press-001"}
          prop {:op :log-production-record :effect :execute}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :no-execution (:rule %)) (:violations verdict))))))

(deftest hard-violations-press-equipment-blocked
  (testing "Hard violation: direct press-equipment operation is permanently blocked"
    (let [s (registered-store)
          req {:press-line-id "press-001"}
          prop {:op :operate-press-equipment :effect :propose}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :equipment-or-certification-blocked (:rule %)) (:violations verdict))))))

(deftest hard-violations-color-certification-blocked
  (testing "Hard violation: finalizing a color-certification decision is permanently blocked"
    (let [s (registered-store)
          req {:press-line-id "press-001"}
          prop {:op :finalize-color-certification :effect :propose}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :equipment-or-certification-blocked (:rule %)) (:violations verdict))))))

(deftest hard-violations-quality-disposition-blocked
  (testing "Hard violation: finalizing a quality-inspection pass/fail disposition is permanently blocked"
    (let [s (registered-store)
          req {:press-line-id "press-001"}
          prop {:op :finalize-quality-disposition :effect :propose}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :equipment-or-certification-blocked (:rule %)) (:violations verdict))))))

(deftest hard-violations-op-not-allowed
  (testing "Hard violation: op outside the closed allowlist"
    (let [s (registered-store)
          req {:press-line-id "press-001"}
          prop {:op :dispatch-inventory-drone :effect :propose}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :op-not-allowed (:rule %)) (:violations verdict))))))

(deftest hard-violations-production-record-invalid
  (testing "Hard violation: non-positive quantity"
    (let [s (registered-store)
          req {:press-line-id "press-001"}
          prop {:op :log-production-record :effect :propose :quantity 0 :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :production-record-invalid (:rule %)) (:violations verdict))))))

(deftest hard-violations-quality-grade-invalid
  (testing "Hard violation: unrecognized quality-disposition grade on log-production-record"
    (let [s (registered-store)
          req {:press-line-id "press-001"}
          prop {:op :log-production-record :effect :propose :quantity 5000
                :quality-grade "AAA+" :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :quality-grade-invalid (:rule %)) (:violations verdict)))))

  (testing "Hard violation: unrecognized quality-disposition grade on log-quality-inspection-record"
    (let [s (registered-store)
          req {:press-line-id "press-001"}
          prop {:op :log-quality-inspection-record :effect :propose
                :run-id "run-1" :quality-grade "excellent!" :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :quality-grade-invalid (:rule %)) (:violations verdict))))))

(deftest hard-violations-print-spec-scope
  (testing "Hard violation: print-spec-id not within the press line's verified scope"
    (let [s (registered-store #{"spec-a"})
          req {:press-line-id "press-001"}
          prop {:op :log-production-record :effect :propose :quantity 5000
                :print-spec-id "spec-z" :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :print-spec-scope-violation (:rule %)) (:violations verdict)))))

  (testing "OK: print-spec-id within the press line's verified scope"
    (let [s (registered-store #{"spec-a"})
          req {:press-line-id "press-001"}
          prop {:op :log-production-record :effect :propose :quantity 5000
                :print-spec-id "spec-a" :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (not (:hard? verdict)))
      (is (:ok? verdict)))))

(deftest hard-violations-release-without-inspection
  (testing "Hard violation: release-print-run with NO quality-inspection record on file"
    (let [s (registered-store #{"spec-a"})
          req {:press-line-id "press-001"}
          prop {:op :release-print-run :effect :propose :run-id "run-1"
                :print-spec-id "spec-a" :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :release-without-quality-inspection (:rule %)) (:violations verdict)))))

  (testing "Hard violation: release-print-run whose inspection on file is NOT passing"
    (let [s (registered-store #{"spec-a"})
          _ (store/record-quality-inspection! s "press-001" "run-1" "fail")
          req {:press-line-id "press-001"}
          prop {:op :release-print-run :effect :propose :run-id "run-1"
                :print-spec-id "spec-a" :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :release-without-quality-inspection (:rule %)) (:violations verdict)))))

  (testing "No hard violation when a PASSING inspection is on file (still always-escalates -- see below)"
    (let [s (registered-store #{"spec-a"})
          _ (store/record-quality-inspection! s "press-001" "run-1" "pass")
          req {:press-line-id "press-001"}
          prop {:op :release-print-run :effect :propose :run-id "run-1"
                :print-spec-id "spec-a" :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (not (:hard? verdict))))))

(deftest ok-production-logging
  (testing "OK: valid production record logging with a registered press line"
    (let [s (registered-store)
          req {:press-line-id "press-001"}
          prop {:op :log-production-record :effect :propose :quantity 5000 :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:hard? verdict)))
      (is (not (:escalate? verdict))))))

(deftest ok-production-logging-with-recognized-quality-grade
  (testing "OK: valid production record logging with a recognized quality-grade code"
    (let [s (registered-store)
          req {:press-line-id "press-001"}
          prop {:op :log-production-record :effect :propose :quantity 5000
                :quality-grade "pass" :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:hard? verdict)))
      (is (not (:escalate? verdict))))))

(deftest ok-quality-inspection-logging
  (testing "OK: valid quality-inspection record logging is a routine coordination op"
    (let [s (registered-store)
          req {:press-line-id "press-001"}
          prop {:op :log-quality-inspection-record :effect :propose
                :run-id "run-1" :quality-grade "pass" :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:escalate? verdict))))))

(deftest escalation-quality-concern
  (testing "Escalation: quality-inspection concern (defect/misregistration/color drift) ALWAYS escalates, even at high confidence"
    (let [s (registered-store)
          req {:press-line-id "press-001"}
          prop {:op :flag-quality-concern :effect :propose
                :concern "スポットカラーのドリフトの可能性" :confidence 0.95}
          verdict (gov/check req nil prop s)]
      (is (not (:hard? verdict)))
      (is (:escalate? verdict))
      (is (:high-stakes? verdict)))))

(deftest escalation-release-print-run-always
  (testing "Escalation: release-print-run ALWAYS escalates even when every hard check is clean and confidence is high (README: 'the governor never releases a print run for delivery itself')"
    (let [s (registered-store #{"spec-a"})
          _ (store/record-quality-inspection! s "press-001" "run-1" "pass")
          req {:press-line-id "press-001"}
          prop {:op :release-print-run :effect :propose :run-id "run-1"
                :print-spec-id "spec-a" :confidence 0.99}
          verdict (gov/check req nil prop s)]
      (is (not (:hard? verdict)))
      (is (:escalate? verdict))
      (is (:high-stakes? verdict))
      (is (not (:ok? verdict))))))

(deftest escalation-low-confidence
  (testing "Escalation: confidence below the floor"
    (let [s (registered-store)
          req {:press-line-id "press-001"}
          prop {:op :log-production-record :effect :propose :quantity 5000 :confidence 0.5}
          verdict (gov/check req nil prop s)]
      (is (not (:hard? verdict)))
      (is (:escalate? verdict)))))

(deftest escalation-supply-order-high-cost
  (testing "Escalation: supply order over the (default) cost threshold"
    (let [s (registered-store)
          req {:press-line-id "press-001"}
          prop {:op :order-supplies :effect :propose :cost 600 :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (not (:hard? verdict)))
      (is (:escalate? verdict)))))

(deftest escalation-supply-order-category-specific-threshold
  (testing "Escalation: supply order over its category-specific threshold (plates: 1500)"
    (let [s (registered-store)
          req {:press-line-id "press-001"}
          prop {:op :order-supplies :effect :propose :cost 1800 :confidence 0.9
                :value {:category "plates"}}
          verdict (gov/check req nil prop s)]
      (is (:escalate? verdict))))

  (testing "OK: plates order under its higher category threshold"
    (let [s (registered-store)
          req {:press-line-id "press-001"}
          prop {:op :order-supplies :effect :propose :cost 1200 :confidence 0.9
                :value {:category "plates"}}
          verdict (gov/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:escalate? verdict))))))

(deftest ok-supply-order-low-cost
  (testing "OK: supply order under the cost threshold"
    (let [s (registered-store)
          req {:press-line-id "press-001"}
          prop {:op :order-supplies :effect :propose :cost 100 :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:escalate? verdict))))))

(deftest ok-schedule-press-operation
  (testing "OK: scheduling a press/finishing operation is a routine coordination op"
    (let [s (registered-store)
          req {:press-line-id "press-001"}
          prop {:op :schedule-press-operation :effect :propose :confidence 0.85}
          verdict (gov/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:escalate? verdict))))))
