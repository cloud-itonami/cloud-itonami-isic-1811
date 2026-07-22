(ns printing.registry-test
  (:require [clojure.test :refer [deftest is testing]]
            [printing.registry :as registry]))

(deftest cost-exceeds-threshold-test
  (testing "Cost within threshold"
    (is (false? (registry/cost-exceeds-threshold? 400 500))))

  (testing "Cost at threshold (inclusive boundary, not exceeded)"
    (is (false? (registry/cost-exceeds-threshold? 500 500))))

  (testing "Cost exceeds threshold"
    (is (true? (registry/cost-exceeds-threshold? 600 500)))))

(deftest quantity-non-positive-test
  (testing "Positive quantity is valid"
    (is (false? (registry/quantity-non-positive? 5000))))

  (testing "Zero quantity is invalid"
    (is (true? (registry/quantity-non-positive? 0))))

  (testing "Negative quantity is invalid"
    (is (true? (registry/quantity-non-positive? -5)))))

(deftest quality-grade-unknown-test
  (testing "Recognized grade codes are known"
    (is (false? (registry/quality-grade-unknown? "pass")))
    (is (false? (registry/quality-grade-unknown? "conditional-pass")))
    (is (false? (registry/quality-grade-unknown? "fail")))
    (is (false? (registry/quality-grade-unknown? "rework-required")))
    (is (false? (registry/quality-grade-unknown? "ungraded"))))

  (testing "An unrecognized grade code is unknown"
    (is (true? (registry/quality-grade-unknown? "AAA+"))))

  (testing "nil grade is unknown"
    (is (true? (registry/quality-grade-unknown? nil)))))

(deftest passing-quality-grade-test
  (testing "Passing grades"
    (is (true? (registry/passing-quality-grade? "pass")))
    (is (true? (registry/passing-quality-grade? "conditional-pass"))))

  (testing "Non-passing recognized grades"
    (is (false? (registry/passing-quality-grade? "fail")))
    (is (false? (registry/passing-quality-grade? "rework-required")))
    (is (false? (registry/passing-quality-grade? "ungraded"))))

  (testing "nil / unrecognized status is not passing"
    (is (false? (registry/passing-quality-grade? nil)))
    (is (false? (registry/passing-quality-grade? "AAA+")))))

(deftest print-spec-verified-test
  (testing "Spec within the press line's own verified scope"
    (is (true? (registry/print-spec-verified?
                {:verified-print-specs #{"spec-a" "spec-b"}} "spec-a"))))

  (testing "Spec outside the press line's verified scope"
    (is (false? (registry/print-spec-verified?
                 {:verified-print-specs #{"spec-a"}} "spec-z"))))

  (testing "Press line with no verified-print-specs at all"
    (is (false? (registry/print-spec-verified? {} "spec-a"))))

  (testing "nil press line (unregistered) never verifies"
    (is (false? (registry/print-spec-verified? nil "spec-a")))))

(deftest confidence-below-floor-test
  (testing "Confidence above floor"
    (is (false? (registry/confidence-below-floor? 0.9 0.7))))

  (testing "Confidence at floor (inclusive, not below)"
    (is (false? (registry/confidence-below-floor? 0.7 0.7))))

  (testing "Confidence below floor"
    (is (true? (registry/confidence-below-floor? 0.5 0.7)))))
