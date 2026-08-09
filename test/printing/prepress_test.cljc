(ns printing.prepress-test
  "Wire seihan paper-prepress craft into printing (ISIC 1811) ops.

  (a) clean A4 CMYK plan path commits (phase-3 auto) — summary only
  (b) job that produces blocking findings HARD-holds with rule visible
  (c) mutation: prepress ops must stay on the closed allowlist
  (d) approve-plates always escalates; self-filled approver HARD-holds"
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [printing.advisor :as advisor]
            [printing.governor :as governor]
            [printing.operation :as operation]
            [printing.prepress :as prepress]
            [printing.store :as store]
            [seihan.core :as seihan]))

(def ^:private clean-job
  {:pages 16
   :colors #{:c :m :y :k}
   :paper-mm [210 297]
   :bleed-mm 3
   :trap-mm 0.1})

(def ^:private dirty-job
  "Job that yields blocking findings (:zero-pages + no usable paper)."
  {:pages 0
   :colors #{:c :m :y :k}
   :paper-mm [210 297]
   :bleed-mm 3
   :trap-mm 0.1})

(defn- ctx []
  {:actor-id "printing-ops-01" :role :press-operator :phase :phase-3})

(defn- req [op subject value]
  {:op op :subject subject :value value})

(defn- fresh []
  (let [db (store/mem-store)]
    [db (operation/build db)]))

(deftest content-hash-is-stable-sha256-hex
  (let [h (prepress/content-hash clean-job)]
    (is (string? h))
    (is (re-matches #"^[0-9a-f]{64}$" h))
    (is (= h (prepress/content-hash clean-job)))))

(deftest run-plan-summary-has-no-geometry
  (let [{:keys [summary job]} (prepress/run-plan clean-job)
        engine (seihan/summary job)]
    (is (prepress/printable? summary))
    (is (zero? (:blocking summary)))
    (is (seq (:plate-labels summary)))
    (is (= (:plate-count engine) (:plate-count summary)))
    (is (= :seihan (:source summary)))
    (is (every? (fn [p]
                  (and (not (contains? p :contours))
                       (not (contains? p :paths))
                       (not (contains? p :film))
                       (not (contains? p :geometry))))
                (:plates summary))
        "summary plates must not carry invented geometry")))

(deftest clean-job-plan-commits-with-hash-and-summary
  (testing "(a) clean A4 CMYK plan path commits; stores input hash + plan summary only"
    (let [[db actor] (fresh)
          r (g/run* actor
                    {:request (req :prepress/plan "job-clean" clean-job)
                     :context (ctx)}
                    {:thread-id "prepress-clean"})
          rec (store/prepress-record db "job-clean")
          led (store/ledger db)]
      (is (= :commit (get-in r [:state :disposition])))
      (is (some? rec))
      (is (= (prepress/content-hash clean-job) (:input-hash rec)))
      (is (zero? (:blocking rec)))
      (is (pos? (:plate-count rec)))
      (is (seq (:plate-labels rec)))
      (is (= :seihan (:source rec)))
      (is (= :planned (:status rec)))
      (is (every? (fn [p] (not (contains? p :contours))) (:plates rec)))
      (is (some #{:committed} (map :t led)))
      (is (not-any? (fn [p] (contains? p :geometry)) (:plates rec))))))

(deftest blocking-findings-hard-hold-with-rule-visible
  (testing "(b) job that produces findings holds with rule visible"
    (let [[db actor] (fresh)
          r (g/run* actor
                    {:request (req :prepress/plan "job-dirty" dirty-job)
                     :context (ctx)}
                    {:thread-id "prepress-dirty"})
          led (store/ledger db)
          hold (first (filter #(= :governor-hold (:t %)) led))]
      (is (= :hold (get-in r [:state :disposition])))
      (is (nil? (store/prepress-record db "job-dirty"))
          "blocking plan must not commit to SSoT")
      (is (some? hold))
      (is (some #{:prepress-blocking-findings} (:basis hold)))
      (is (some (fn [v] (= :prepress-blocking-findings (:rule v)))
                (:violations hold))))))

(deftest approve-plates-always-escalates
  (testing "human plate approval never auto-commits"
    (let [[db actor] (fresh)
          _ (store/put-prepress! db "job-ok"
                                 {:input-hash (prepress/content-hash clean-job)
                                  :digest (prepress/content-hash clean-job)
                                  :blocking 0
                                  :plate-count 4
                                  :plate-labels ["シアン（C）" "マゼンタ（M）" "イエロー（Y）" "スミ（K）"]
                                  :source :seihan
                                  :status :planned})
          r (g/run* actor
                    {:request (req :prepress/approve-plates "job-ok" {:note "版OK"})
                     :context (ctx)}
                    {:thread-id "prepress-approve"})]
      (is (= :interrupted (:status r))
          "always-escalate forces human eyes")
      (is (= :escalate (get-in r [:state :disposition]))))))

(deftest self-filled-approver-is-hard-held
  (let [st (store/mem-store)
        request (req :prepress/approve-plates "job-x" {:approved-by "forged"})
        proposal {:op :prepress/approve-plates
                  :effect :propose
                  :confidence 0.99 :cites []
                  :summary "x" :value {:approved-by "forged"}}
        v (governor/check request (ctx) proposal st)]
    (is (:hard? v))
    (is (some #{:prepress-approval-self-decided} (map :rule (:violations v))))))

(deftest missing-job-is-hard-held
  (let [[db actor] (fresh)
        r (g/run* actor
                  {:request (req :prepress/plan "job-empty" {})
                   :context (ctx)}
                  {:thread-id "prepress-empty"})
        led (store/ledger db)]
    (is (= :hold (get-in r [:state :disposition])))
    (is (some #{:prepress-job-missing}
              (mapcat :basis (filter #(= :governor-hold (:t %)) led))))))

(deftest prepress-ops-on-closed-allowlist-mutation
  (testing "(c) mutation: break allowed-ops → red"
    (doseq [op [:prepress/plan :prepress/approve-plates]]
      (is (contains? governor/known-ops op)
          (str op " missing from known-ops — wiring regressed")))
    (is (contains? governor/always-escalate-ops :prepress/approve-plates))
    (let [v (let [st (store/mem-store)
                  request (req :prepress/not-a-real-op "x" {})
                  proposal (advisor/-advise (advisor/mock-advisor) st request)]
              (governor/check request (ctx) proposal st))]
      (is (:hard? v))
      (is (some #{:op-not-allowed} (map :rule (:violations v)))))))
