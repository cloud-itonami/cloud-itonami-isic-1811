(ns printing.governor-contract-test
  (:require [clojure.test :refer [deftest is]]
            [printing.store :as store]
            [printing.advisor :as advisor]
            [printing.governor :as governor]
            [printing.registry :as registry]))

(deftest spec-basis-hard-gate
  "Spec-basis is a HARD gate: never allow proposals without official citations."
  (let [st (store/mem-store)
        proposal {:op :actuation/schedule-production-batch
                  :subject "batch-001"
                  :effect :propose
                  :value {:evidence {:batch-verified true}
                          :confidence 0.9}
                  :cites []}]
    (let [eval (governor/evaluate proposal st)]
      (is (:holds? eval) "Proposal with empty cites should hold")
      (is (seq (:hard-violations eval)) "Should have hard violations")
      (is (some #(= (:rule %) :no-spec-basis) (:hard-violations eval))))))

(deftest equipment-control-block
  "HARD BLOCK: Proposals mentioning press control, ink adjustment, or equipment
  parameters are immediately rejected. Those remain exclusive to qualified operators."
  (let [st (store/mem-store)
        proposal {:op :actuation/schedule-production-batch
                  :subject "batch-001"
                  :effect :propose
                  :cites ["some-spec"]
                  :value {:evidence {:batch-verified true}
                          :confidence 0.9
                          :detail "Please set press temperature to 80C and adjust ink flow"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (:holds? eval) "Equipment-control proposal should hold")
      (is (some #(= (:rule %) :equipment-control-forbidden) (:hard-violations eval))
        "Should have equipment-control-forbidden violation"))))

(deftest quality-defect-escalation
  "Quality defects ALWAYS escalate to human. Never silently log a defect."
  (let [st (store/mem-store)
        proposal {:op :actuation/flag-quality-defect
                  :subject "job-001"
                  :effect :propose
                  :cites ["Product Liability Law §3"]
                  :value {:evidence {:quality-inspection-report true}
                          :confidence 0.95
                          :has-quality-issue? true
                          :detail "Color accuracy out of tolerance"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (:holds? eval) "Quality defect should hold")
      (is (some #(= (:rule %) :quality-defect-escalation) (:hard-violations eval))
        "Should have quality-defect-escalation violation"))))

(deftest actuation-requires-escalation
  "Production batch scheduling and quality flagging require human sign-off,
  even when all other checks are clean."
  (let [st (store/mem-store)
        adv (advisor/mock-advisor)
        batch-proposal (advisor/schedule-production-batch adv "batch-001")]
    (let [eval (governor/evaluate batch-proposal st)]
      (is (seq (:soft-violations eval)) "Should have soft violations for actuation")
      (is (some #(= (:rule %) :escalate) (:soft-violations eval))
        "Should escalate high-stakes actuation"))))

(deftest batch-not-verified-blocks-production
  "Production batch scheduling with unverified batch is blocked."
  (let [st (store/mem-store)
        ;; Create a production run with unverified batch
        _ (swap! (-> st :data) assoc-in [:batches "batch-003" :verified?] false)
        proposal (registry/schedule-production-batch-draft "batch-003"
                   ["Occupational Safety and Health Act §20"]
                   {:batch-verified true}
                   0.85
                   "Schedule production")]
    (let [eval (governor/evaluate proposal st)]
      (is (seq (:hard-violations eval)) "Should have hard violations")
      (is (some #(= (:rule %) :batch-not-verified) (:hard-violations eval))
        "Should block unverified batch"))))

(deftest low-confidence-escalates
  "Low confidence proposals escalate to human, even if otherwise clean."
  (let [st (store/mem-store)
        proposal {:op :proposal/log-production-batch
                  :subject "batch-001"
                  :effect :propose
                  :cites ["Labor Standards Act §36"]
                  :value {:evidence {:batch-registered true}
                          :confidence 0.45
                          :detail "Batch logged"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (seq (:soft-violations eval)) "Should have soft violations")
      (is (some #(= (:rule %) :escalate) (:soft-violations eval))
        "Should escalate low-confidence"))))

(deftest clean-proposal
  "A proposal with all evidence, valid spec-basis, high confidence,
  and no high-stakes actuation or equipment-control is clean."
  (let [st (store/mem-store)
        proposal {:op :proposal/coordinate-shipment
                  :subject "shipment-001"
                  :effect :propose
                  :cites ["Transport Safety Regulation"]
                  :value {:evidence {:quality-final-check true :packing-certified true}
                          :confidence 0.9
                          :detail "Shipment ready for dispatch"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (:clean? eval) "Should be clean")
      (is (empty? (:hard-violations eval)) "Should have no hard violations"))))

(deftest maintenance-proposal
  "Maintenance scheduling is a routine proposal (propose only, no escalation)."
  (let [st (store/mem-store)
        proposal {:op :proposal/schedule-maintenance
                  :subject "press-001"
                  :effect :propose
                  :cites ["Occupational Safety and Health Act §30"]
                  :value {:evidence {:maintenance-schedule true :technician-available true}
                          :confidence 0.82
                          :detail "Equipment maintenance scheduled"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (:clean? eval) "Should be clean")
      (is (empty? (:hard-violations eval)) "Should have no hard violations"))))
