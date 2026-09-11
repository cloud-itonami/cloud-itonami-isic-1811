(ns printing.facts-test
  (:require [clojure.test :refer [deftest is are testing]]
            [printing.facts :as facts]))

(deftest supply-category-lookup
  (testing "Lookup valid supply category"
    (let [c (facts/supply-category-by-id "ink")]
      (is (= "ink" (:id c)))
      (is (= "インキ" (:name c)))))

  (testing "Lookup invalid supply category"
    (is (nil? (facts/supply-category-by-id "unknown")))))

(deftest supply-category-cost-thresholds
  (testing "Category-specific cost thresholds"
    (are [id expected] (= expected (:cost-threshold (facts/supply-category-by-id id)))
      "ink"       500
      "substrate" 500
      "plates"    1500)))

(deftest default-cost-threshold-value
  (testing "Default fallback threshold matches the conservative baseline"
    (is (= 500 facts/default-cost-threshold))))

(deftest press-type-lookup
  (testing "Lookup valid press type"
    (are [id expected-name] (= expected-name (:name (facts/press-type-by-id id)))
      "offset"       "オフセット印刷"
      "digital"      "デジタル印刷"
      "flexographic" "フレキソ印刷"))

  (testing "Lookup invalid press type"
    (is (nil? (facts/press-type-by-id "unknown"))))

  (testing "Out-of-scope printing-support activity (ISIC 1812, not this actor) is not a press type"
    (is (nil? (facts/press-type-by-id "plate-making-service")))))

(deftest press-operation-types-reference-set
  (testing "Printing-specific press/finishing operation types are present"
    (is (contains? facts/press-operation-types "press-feed"))
    (is (contains? facts/press-operation-types "quality-inspection"))
    (is (contains? facts/press-operation-types "finishing"))
    (is (contains? facts/press-operation-types "binding"))
    (is (contains? facts/press-operation-types "color-calibration")))

  (testing "Not a validated enum -- an unlisted operation type is simply absent"
    (is (not (contains? facts/press-operation-types "die-cutting")))))

(deftest quality-grades-closed-set
  (testing "Recognized quality-disposition grade codes are present"
    (is (contains? facts/quality-grades "pass"))
    (is (contains? facts/quality-grades "conditional-pass"))
    (is (contains? facts/quality-grades "fail"))
    (is (contains? facts/quality-grades "rework-required"))
    (is (contains? facts/quality-grades "ungraded")))

  (testing "An unrecognized grade code is absent from the closed vocabulary"
    (is (not (contains? facts/quality-grades "AAA+")))
    (is (not (contains? facts/quality-grades "")))))

(deftest passing-quality-grades-subset
  (testing "Passing grades are a subset of the closed quality-grades vocabulary"
    (is (every? facts/quality-grades facts/passing-quality-grades)))

  (testing "pass and conditional-pass count as passing"
    (is (contains? facts/passing-quality-grades "pass"))
    (is (contains? facts/passing-quality-grades "conditional-pass")))

  (testing "fail/rework-required/ungraded do NOT count as passing"
    (is (not (contains? facts/passing-quality-grades "fail")))
    (is (not (contains? facts/passing-quality-grades "rework-required")))
    (is (not (contains? facts/passing-quality-grades "ungraded")))))
