(ns printing.advisor
  "PrintingOpsAdvisor -- the contained LLM/decision node. This actor's
  intelligence layer proposes back-office coordination actions (print-run
  production and quality-inspection record logging, press/finishing
  operation scheduling, quality concern flags, supply procurement, print-
  run release for delivery) based on press-line state and operator input.
  The advisor is SEALED into the `:advise` step of the operation flow;
  every proposal is routed through the independent Printing Governor
  before committing.

  The advisor makes proposals but has NO direct authority. Proposals are
  always censored by:
    1. Governor (press-line registration, closed-op allowlist,
       print-spec-scope/quality-inspection/cost gates)
    2. Phase gate (rollout stage)
    3. Human operator (for escalated actions)

  FIX (deferred-stub + fabrication removed): a prior attempt at this repo
  defined loose top-level functions (`log-production-batch` etc, not an
  `Advisor` protocol) that each hardcoded a fabricated statute citation
  in `:cites` (e.g. 'Labor Standards Act (労働基準法) §36', 'Product
  Liability Law (製造物責任法) §3', 'Transport Safety Regulation
  (運輸安全管理規則)') -- none of which are grounded in this repo's own
  docs. Replaced with the `Advisor` protocol + `MockAdvisor` shape every
  sibling actor uses (mirrors `tobaccoops.advisor`, cloud-itonami-isic-
  0115) and structural, non-regulatory `:cites` (what evidence the
  proposal is based on, not a claimed legal citation).

  Current implementation is a mock advisor for testing. Production should
  use langchain/Claude or similar LLM backend (same seam point as
  `tobaccoops.advisor`).

  Prepress craft (paper seihan, not garment shirohan):
    :prepress/plan            — call seihan.core/plan; propose summary only
    :prepress/approve-plates  — human plate approval (always escalate)"
  (:require [printing.prepress :as prepress]
            [printing.store :as store]))

;; Protocol for swappable advisor implementations
(defprotocol Advisor
  (-advise [advisor store request]
    "Given store and request, return a proposal map with :op, :effect,
    :value, :cites, :summary, :confidence (plus any op-specific top-level
    keys the Governor independently verifies, e.g.
    :quantity/:quality-grade/:print-spec-id/:run-id/:cost)."))

(defn- prepress-plan
  "Call seihan once; propose a *summary-only* record (input hash + plate
  labels + finding counts). Never put invented plate geometry on the
  proposal value.

  Confidence drops when the job map is missing or the engine reports
  blocking findings — governor HARD-holds those; confidence stays honest
  for humans reading the audit trail."
  [_db request]
  (let [subject (prepress/subject-of request)
        value (or (:value request) request)
        result (prepress/run-plan value)
        err (:error result)
        summary (:summary result)]
    (cond
      (= err :job-missing)
      {:op         :prepress/plan
       :effect     :propose
       :summary    (str "prepress/plan " subject " — job map が無い")
       :cites      []
       :value      {:prepress nil :error :job-missing}
       :confidence 0.1}

      (prepress/blocking-findings? summary)
      {:op         :prepress/plan
       :effect     :propose
       :summary    (str "prepress/plan " subject " — blocking 所見 "
                        (:blocking summary) " 件（入力 " (:input-hash summary) "）")
       :cites      [:prepress/input-hash :prepress/blocking]
       :value      {:prepress summary}
       :confidence 0.2}

      :else
      {:op         :prepress/plan
       :effect     :propose
       :summary    (str "prepress/plan " subject " — 版 "
                        (:plate-count summary) " 枚 / hash "
                        (when-let [h (:input-hash summary)]
                          (subs h 0 (min 12 (count h)))))
       :cites      [:prepress/input-hash :prepress/plate-labels]
       :value      {:prepress summary}
       :confidence 0.95})))

(defn- prepress-approve-plates
  "Human plate approval — always high-stakes. Actor never self-approves."
  [db request]
  (let [subject (prepress/subject-of request)
        value (or (:value request) {})
        rec (store/prepress-record db subject)
        printable? (prepress/printable? rec)]
    {:op         :prepress/approve-plates
     :effect     :propose
     :summary    (str "prepress/approve-plates " subject " — 人による版承認を求める")
     :cites      (if rec [:prepress/input-hash] [])
     ;; never pre-fill approver — human-in-the-loop resume writes it
     :value      (dissoc (or value {}) :approved-by)
     :confidence (if printable? 0.9 0.3)}))

;; Mock advisor for testing
(defrecord MockAdvisor []
  Advisor
  (-advise [_advisor store request]
    (let [{:keys [op press-line-id]} request]
      (case op
        :log-production-record
        {:op :log-production-record
         :effect :propose
         :quantity (:quantity request 0)
         :quality-grade (:quality-grade request "ungraded")
         :print-spec-id (:print-spec-id request)
         :value {:press-line-id press-line-id
                 :quantity (:quantity request 0)
                 :press-type (:press-type request "offset")
                 :print-spec-id (:print-spec-id request)
                 :quality-grade (:quality-grade request "ungraded")
                 :run-id (:run-id request)}
         :cites ["operator-submitted-production-data"]
         :summary "Print-run production record entry logged from operator submission"
         :confidence 0.9}

        :log-quality-inspection-record
        {:op :log-quality-inspection-record
         :effect :propose
         :run-id (:run-id request)
         :quality-grade (:quality-grade request "ungraded")
         :value {:press-line-id press-line-id
                 :run-id (:run-id request)
                 :quality-grade (:quality-grade request "ungraded")
                 :inspector (:inspector request "unspecified")}
         :cites ["quality-inspection-camera-pass" "operator-submitted-inspection-data"]
         :summary "Quality-inspection record entry logged for print run"
         :confidence 0.9}

        :schedule-press-operation
        {:op :schedule-press-operation
         :effect :propose
         :value {:press-line-id press-line-id
                 :operation-type (:operation-type request "press-feed")
                 :requested-date (:requested-date request)
                 :reason (:reason request "routine-schedule")}
         :cites ["operator-scheduling-request"]
         :summary "Press/finishing operation (press-feed/quality-inspection/finishing/binding/color-calibration) proposed per operator request"
         :confidence 0.85}

        :flag-quality-concern
        {:op :flag-quality-concern
         :effect :propose
         :concern (:concern request "unspecified concern")
         :value {:press-line-id press-line-id
                 :concern (:concern request "unspecified concern")
                 :recommended-action "quality-inspector-review"}
         :cites ["operator-observation"]
         :summary "Quality-inspection concern (defect, misregistration, color drift) flagged for quality-inspector review"
         :confidence 0.8}

        :order-supplies
        {:op :order-supplies
         :effect :propose
         :cost (:cost request 0)
         :value {:press-line-id press-line-id
                 :category (:category request "ink")
                 :cost (:cost request 0)}
         :cites ["operator-procurement-request"]
         :summary "Supply order (ink/substrate/plates) proposed for press line"
         :confidence 0.85}

        :release-print-run
        {:op :release-print-run
         :effect :propose
         :run-id (:run-id request)
         :print-spec-id (:print-spec-id request)
         :value {:press-line-id press-line-id
                 :run-id (:run-id request)
                 :print-spec-id (:print-spec-id request)}
         :cites ["quality-inspection-record-on-file" "print-specification-scope-verification"]
         :summary "Print-run release for delivery proposed, pending governor/human sign-off"
         :confidence 0.9}

        :prepress/plan
        (prepress-plan store request)

        :prepress/approve-plates
        (prepress-approve-plates store request)

        ;; fallback -- unrecognized op. The Governor's closed allowlist
        ;; independently rejects this regardless of what the advisor says.
        {:op op
         :effect :propose
         :value {}
         :cites []
         :summary "Operation not recognized"
         :confidence 0.0}))))

(defn mock-advisor []
  (MockAdvisor.))

(defn trace
  "Audit trail entry for an advisor proposal. Recorded whenever a proposal
  is generated, regardless of whether it's approved."
  [request proposal]
  {:t :advisor-proposal
   :op (:op request)
   :press-line-id (:press-line-id request)
   :subject (prepress/subject-of request)
   :proposal-summary (:summary proposal)
   :confidence (:confidence proposal)})
