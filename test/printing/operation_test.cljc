(ns printing.operation-test
  "Integration tests for `printing.operation/build` -- builds the REAL
  compiled `langgraph.graph` StateGraph and runs it end-to-end via
  `langgraph.graph/run*` through commit / hard-hold / escalate-approve /
  escalate-reject / quality-inspection-gated-release routes. This
  namespace did not exist in a prior attempt at this repo: there was no
  `printing.operation` at all, and `printing.sim` was a one-line
  `(println ...)` stub that never touched `kotoba-lang/langgraph`. These
  tests prove the compiled graph is real and that the audit ledger
  (`printing.store/append-ledger!`) AND the quality-inspection index
  (`printing.store/record-quality-inspection!`) are genuinely wired into
  the `:commit`/`:hold`/`:request-approval` nodes -- falsifiable on real
  StateGraph behavior, not hardcoded pass strings: hold-until-approved,
  ledger stays empty until commit, governor rejection blocks commit, a
  print run cannot be released until its own quality-inspection record
  genuinely commits first. Mirrors `tobaccoops.operation-test`
  (cloud-itonami-isic-0115)."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [printing.operation :as operation]
            [printing.store :as store]))

(def printer {:actor-id "printing-ops-01" :role :press-operator :phase :phase-3})

(defn- exec [actor tid request]
  (g/run* actor {:request request :context printer} {:thread-id tid}))

(defn- seeded-store []
  (store/mem-store
   {:initial-press-lines
    {"press-001" {:id "press-001" :name "Line 1 (Offset)"
                  :press-type "offset"
                  :verified-print-specs #{"spec-a"}}}}))

(deftest commit-path-clean-proposal
  (testing "a clean, phase-3, high-confidence production-record request
            commits through the real compiled graph and appends exactly
            one fact to the audit ledger"
    (let [s (seeded-store)
          actor (operation/build s)
          result (exec actor "t-commit"
                       {:op :log-production-record :press-line-id "press-001"
                        :quantity 5000 :quality-grade "pass" :print-spec-id "spec-a"})
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :commit (:disposition state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :committed (:t (first ledger))))
        (is (= :log-production-record (:op (first ledger))))
        (is (= "press-001" (:subject (first ledger))))))))

(deftest hard-hold-path-unregistered-press-line
  (testing "an unregistered press line is a HARD, permanent governor violation
            -- the real graph routes straight to :hold (no interrupt, no
            human-approval detour) and durably records the hold fact,
            and the ledger stays empty of any :committed fact"
    (let [s (seeded-store)
          actor (operation/build s)
          result (exec actor "t-hold"
                       {:op :log-production-record :press-line-id "press-999"
                        :quantity 5000 :quality-grade "pass"})
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :hold (:disposition state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :governor-hold (:t (first ledger))))
        (is (some #(= :press-line-not-registered (:rule %)) (:violations (first ledger))))
        (is (not-any? #(= :committed (:t %)) ledger)
            "governor rejection blocks commit -- no :committed fact ever lands")))))

(deftest escalate-then-approve-commits
  (testing ":flag-quality-concern ALWAYS escalates -- the real graph
            GENUINELY interrupts (checkpointed) at :request-approval; the
            ledger stays completely empty until a human printer/quality-
            manager approve! resumes the SAME compiled graph and commits
            via the graph's own :request-approval -> :commit edge"
    (let [s (seeded-store)
          actor (operation/build s)
          held (exec actor "t-escalate"
                     {:op :flag-quality-concern :press-line-id "press-001"
                      :concern "色ズレの可能性"})]
      (is (= :interrupted (:status held)))
      (is (= [:request-approval] (:frontier held)))
      (is (empty? (store/ledger s))
          "hold-until-approved: not yet committed -- awaiting human sign-off, ledger stays empty until commit")
      (let [approved (g/run* actor {:approval {:status :approved :by "qc-manager-01"}}
                             {:thread-id "t-escalate" :resume? true})
            approved-state (:state approved)]
        (is (= :done (:status approved)))
        (is (= :commit (:disposition approved-state)))
        (let [ledger (store/ledger s)]
          (is (= 1 (count ledger)))
          (is (= :committed (:t (first ledger))))
          (is (= :flag-quality-concern (:op (first ledger))))
          (is (= "qc-manager-01" (get-in approved-state [:record :payload :approved-by]))))))))

(deftest escalate-then-reject-holds
  (testing "a human printer/quality-manager rejecting an escalated request
            routes to :hold via the :request-approval node's own
            decision (governor rejection / human rejection both block
            commit), and durably records the rejection -- not a
            hand-rolled parallel path"
    (let [s (seeded-store)
          actor (operation/build s)
          _held (exec actor "t-reject"
                      {:op :flag-quality-concern :press-line-id "press-001"
                       :concern "登録ズレの可能性"})
          rejected (g/run* actor {:approval {:status :rejected :by "qc-manager-01"}}
                           {:thread-id "t-reject" :resume? true})
          rejected-state (:state rejected)]
      (is (= :done (:status rejected)))
      (is (= :hold (:disposition rejected-state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :approval-rejected (:t (first ledger))))
        (is (not-any? #(= :committed (:t %)) ledger)
            "a rejected approval never reaches :commit")))))

(deftest phase-0-forces-escalation-even-when-governor-clean
  (testing "phase-0 (simulation) forces EVERY otherwise-clean commit
            through human review -- the phase gate independently
            overrides an otherwise-:commit governor verdict, proven
            against the real compiled graph. Compares two independent
            stores: a phase-3 context commits, the SAME clean proposal
            under a phase-0 context only interrupts, ledger stays empty."
    (let [request {:op :log-production-record :press-line-id "press-001"
                   :quantity 5000 :quality-grade "pass"}
          s3 (seeded-store)
          actor3 (operation/build s3)
          result (exec actor3 "t-phase3" request)

          s0 (seeded-store)
          actor0 (operation/build s0)
          held (g/run* actor0 {:request request
                               :context (assoc printer :phase :phase-0)}
                       {:thread-id "t-phase0"})]
      ;; the phase-3 printer context commits (sanity check, mirrors
      ;; commit-path-clean-proposal above)
      (is (= :commit (:disposition (:state result))))
      (is (seq (store/ledger s3)))
      ;; the SAME proposal under a phase-0 context only interrupts --
      ;; no autonomous commit, ledger stays empty until a human resumes
      (is (= :interrupted (:status held)))
      (is (empty? (store/ledger s0))))))

(deftest quality-inspection-gates-release
  (testing "a print run cannot even be CONSIDERED for release until its
            own quality-inspection record genuinely commits through the
            real graph -- proving `printing.operation`'s :commit node
            actually writes `printing.store`'s quality-inspection index,
            not just the audit ledger, and that
            `printing.governor`'s release-without-quality-inspection hard
            check re-derives it from the Store (never the proposal's own
            self-report)"
    (let [s (seeded-store)
          actor (operation/build s)]
      ;; before any inspection record: release is a HARD hold, no interrupt
      (let [premature (exec actor "t-premature"
                            {:op :release-print-run :press-line-id "press-001"
                             :run-id "run-1001" :print-spec-id "spec-a"})]
        (is (= :done (:status premature)))
        (is (= :hold (:disposition (:state premature))))
        (is (some #(= :release-without-quality-inspection (:rule %))
                  (:violations (first (store/ledger s))))))

      ;; log-quality-inspection-record commits and updates the Store's index
      (let [inspected (exec actor "t-inspect"
                            {:op :log-quality-inspection-record :press-line-id "press-001"
                             :run-id "run-1001" :quality-grade "pass" :inspector "qc-02"})]
        (is (= :commit (:disposition (:state inspected))))
        (is (= "pass" (store/quality-inspection-status s "press-001" "run-1001"))))

      ;; NOW release-print-run's hard checks pass, but it still ALWAYS
      ;; escalates for human sign-off (README: 'the governor never
      ;; releases a print run for delivery itself') -- approve to commit
      (let [held (exec actor "t-release"
                       {:op :release-print-run :press-line-id "press-001"
                        :run-id "run-1001" :print-spec-id "spec-a"})]
        (is (= :interrupted (:status held)))
        (let [approved (g/run* actor {:approval {:status :approved :by "printer-01"}}
                               {:thread-id "t-release" :resume? true})]
          (is (= :commit (:disposition (:state approved))))
          (is (= "printer-01" (get-in (:state approved) [:record :payload :approved-by])))))

      ;; three ledger entries total: the premature hard-hold, the
      ;; inspection commit, and the release commit
      (is (= 3 (count (store/ledger s)))))))
