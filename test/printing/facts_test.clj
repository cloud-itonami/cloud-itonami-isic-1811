(ns printing.facts-test
  (:require [clojure.test :refer [deftest is]]
            [printing.facts :as facts]))

(deftest jurisdiction-catalog-present
  "Jurisdiction catalog should have at least the starting jurisdictions."
  (let [cov (facts/coverage)]
    (is (>= (:implemented cov) 3) "Should have at least 3 jurisdictions")
    (is (some? (:coverage-pct cov)) "Should report coverage percentage")))

(deftest japan-requirements
  "Japan jurisdiction should have required regulations."
  (let [reqs (facts/requirement-citations :JPN)]
    (is (some? (:worker-safety reqs)) "Should have worker-safety requirement")
    (is (some? (:quality-assurance reqs)) "Should have quality-assurance requirement")
    (is (some? (:equipment-maintenance reqs)) "Should have equipment-maintenance requirement")))

(deftest usa-requirements
  "USA jurisdiction should have required regulations."
  (let [reqs (facts/requirement-citations :USA)]
    (is (some? (:worker-safety reqs)) "Should have worker-safety requirement")
    (is (some? (:quality-assurance reqs)) "Should have quality-assurance requirement")))

(deftest gbr-requirements
  "UK jurisdiction should have required regulations."
  (let [reqs (facts/requirement-citations :GBR)]
    (is (some? (:worker-safety reqs)) "Should have worker-safety requirement")))

(deftest evidence-satisfaction
  "Evidence checklist validation should work correctly."
  (let [japan-reqs (facts/requirement-citations :JPN)
        sufficient-evidence {:safety-plan true
                            :worker-training-records true
                            :hazard-assessment true
                            :quality-inspection-procedure true
                            :color-profile-verification true
                            :customer-spec true
                            :maintenance-schedule true
                            :equipment-inspection-log true
                            :calibration-cert true
                            :msds-sheets true
                            :chemical-storage-cert true
                            :disposal-plan true}
        insufficient-evidence {:safety-plan true}]
    (is (true? (facts/required-evidence-satisfied? :JPN sufficient-evidence))
      "Complete evidence checklist should satisfy")
    (is (false? (facts/required-evidence-satisfied? :JPN insufficient-evidence))
      "Incomplete evidence checklist should not satisfy")))
