(ns printing.store-contract-test
  (:require [clojure.test :refer [deftest is]]
            [printing.store :as store]))

(deftest mem-store-creation
  "Store is initialized with reference data."
  (let [st (store/mem-store)]
    (is (some? (:data st)) "Store should have data atom")
    (is (some? (store/batch st "batch-001")) "Should have batch-001")
    (is (some? (store/job st "job-001")) "Should have job-001")))

(deftest batch-verification
  "Batch verification guards work correctly."
  (let [st (store/mem-store)]
    (is (true? (store/batch-verified? st "batch-001")) "batch-001 should be verified")
    (is (false? (store/batch-verified? st "batch-002")) "batch-002 should not be verified")
    (is (false? (store/batch-verified? st "nonexistent")) "Nonexistent batch should return false")))

(deftest equipment-operational
  "Equipment status check works correctly."
  (let [st (store/mem-store)]
    (is (true? (store/equipment-operational? st "press-001")) "press-001 should be operational")
    (is (true? (store/equipment-operational? st "press-002")) "press-002 should be operational")))

(deftest quality-issue-detection
  "Quality check detection works correctly."
  (let [st (store/mem-store)]
    (is (false? (store/quality-issue-exists? st "qc-001")) "qc-001 should pass")
    (is (true? (store/quality-issue-exists? st "qc-002")) "qc-002 should have issue")))

(deftest shipment-readiness
  "Shipment readiness check works correctly."
  (let [st (store/mem-store)]
    (is (true? (store/shipment-ready? st "shipment-001")) "shipment-001 should be ready")))
