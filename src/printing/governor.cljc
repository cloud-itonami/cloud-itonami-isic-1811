(ns printing.governor
  "Printing Governor -- the independent compliance layer that earns the
  PrintingOpsAdvisor the right to commit. The LLM has no notion of:
    - Whether the press line a proposal targets is actually registered
    - Whether a proposal is a real actuation (`:effect :propose` only --
      this actor NEVER directly operates the press or finalizes a color-
      certification/quality-disposition decision)
    - Whether an op is inside this actor's closed coordination allowlist
    - Whether a logged production-record quantity is a plausible positive
      observation
    - Whether a logged quality-disposition grade is a recognized grade
      code
    - Whether a print-spec-id a proposal cites is actually within the
      press line's OWN verified color/print-specification scope
    - Whether a print-run release cites a run that actually has a
      completed, passing quality-inspection record on file
    - Whether a supply-order's cost exceeds the escalation threshold

  This MUST be a separate system able to *reject* a proposal and fall
  back to HOLD.

  This actor is a back-office PRINTING OPERATIONS COORDINATOR only --
  direct press-equipment operation and finalizing a color-certification
  or quality-inspection disposition decision are categorically outside
  its authority (printer/quality-control-manager exclusive). The Governor
  enforces that boundary structurally, not by trusting the advisor's
  judgment.

  CRITICAL: Per this repo's own README ('the governor never releases a
  print run for delivery itself'), `:release-print-run` ALWAYS escalates
  to a human for final sign-off, even when every hard check is clean --
  see `always-escalate-ops` below.

  Hard violations (always HOLD, no override, permanent):
    1. Press line not registered (press-line-id missing or unknown to
       Store)
    2. Proposal `:effect` is not `:propose` (no direct execution, ever)
    3. Op is `:operate-press-equipment`, `:finalize-color-certification`,
       or `:finalize-quality-disposition` -- direct press-equipment
       operation, finalizing a color-management certification, and
       finalizing a quality-inspection pass/fail disposition are
       PERMANENTLY blocked regardless of proposal content or confidence
    4. Op is outside the closed proposal-op allowlist
    5. `:log-production-record` with a non-positive quantity
    6. A `:quality-grade` (on `:log-production-record` or `:log-quality-
       inspection-record`) that is not a recognized grade code
    7. A `:print-spec-id` (on `:log-production-record` or
       `:release-print-run`) that is not within the press line's own
       verified-print-specs scope (README: 'a print run cannot be
       released outside its verified color/specification scope')
    8. `:release-print-run` whose run-id has no completed, PASSING
       quality-inspection record on file in the Store (README: 'a
       delivery release without a completed quality-inspection pass ...
       require[s] human sign-off') -- re-derived from the Store's
       quality-inspection index, never from the proposal's self-report

  Soft gates (always escalate for human):
    - `:flag-quality-concern` -- ALWAYS escalates
    - `:release-print-run` -- ALWAYS escalates ('the governor never
      releases a print run for delivery itself')
    - `:order-supplies` above its category cost threshold
    - Low confidence

  This design mirrors `tobaccoops.governor` (cloud-itonami-isic-0115) but
  specializes printing-plant back-office coordination concerns (press-
  line registration, closed op allowlist, equipment/color-certification/
  quality-disposition exclusion, print-spec-scope verification, quality-
  inspection-gated release, cost threshold, quality-grade vocabulary)
  rather than tobacco-growing concerns.

  FIX (deferred-stub bug + fabrication removed): a prior attempt at this
  repo (a) had no notion of press lines, print-spec-scope, or quality-
  inspection-gated release -- the actual ISIC-1811 domain vocabulary this
  README describes -- and instead modeled a generic 'plant job/batch/
  shipment' domain unrelated to it; (b) its 'equipment-control-forbidden'
  hard check keyword-matched a free-text `:detail` string against a
  keyword set that included the literal word 'press' -- meaning ANY
  proposal whose detail happened to contain the word 'press' (unavoidable
  in a printing-press domain) would hard-block, a structurally broken
  gate; (c) required a fabricated `:spec-basis` regulatory citation per
  proposal. All replaced below with the structural, ground-truth-in-the-
  Store gates this domain's own README specifies."
  (:require [printing.facts :as facts]
            [printing.registry :as registry]
            [printing.store :as store]))

(def confidence-floor 0.7)

(def blocked-ops
  "Direct press-equipment operation, finalizing a color-certification
  decision, and finalizing a quality-inspection pass/fail disposition sit
  outside this actor's coordination-only authority. ALWAYS a hard,
  permanent block -- never escalate, never override, regardless of
  confidence or cites."
  #{:operate-press-equipment
    :finalize-color-certification
    :finalize-quality-disposition})

(def known-ops
  "The closed allowlist of proposal ops this actor may make -- all
  `:effect :propose` (see ADR domain design)."
  #{:log-production-record :log-quality-inspection-record
    :schedule-press-operation :flag-quality-concern :order-supplies
    :release-print-run})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off even when the Governor finds no
  hard violation and confidence is high. Flagging a quality concern and
  releasing a print run for delivery are never something this actor
  resolves autonomously (README: 'the governor never releases a print
  run for delivery itself')."
  #{:flag-quality-concern :release-print-run})

(def all-recognized-ops
  "known-ops (allowed to proceed) union blocked-ops (recognized but
  permanently forbidden). Anything outside this union is an unknown op --
  a HARD violation, not a silent no-op."
  (into known-ops blocked-ops))

;; ----------------------------- checks -----------------------------

(defn- press-line-violations
  "A proposal referencing an unregistered (or absent) press-line-id is a
  HARD violation -- never act on behalf of a press line this actor cannot
  independently verify."
  [{:keys [press-line-id]} st]
  (when-not (store/registered-press-line st press-line-id)
    [{:rule :press-line-not-registered
      :detail (str "press-line-id " (pr-str press-line-id) " は登録済みの印刷ラインとして確認できない -- ライン登録前の提案は進められない")}]))

(defn- execution-violations
  "This actor never executes directly. Any proposal whose `:effect` isn't
  `:propose` is a HARD violation, independent of what op it claims."
  [proposal]
  (when-not (= :propose (:effect proposal))
    [{:rule :no-execution
      :detail "提案の :effect は :propose でなければならない -- governor は直接実行/作動を許可しない"}]))

(defn- equipment-or-certification-violations
  "Direct press-equipment operation, finalizing a color-certification
  decision, and finalizing a quality-inspection pass/fail disposition are
  a HARD, permanent block -- press operation, color-management
  certification, and quality-disposition authority remain exclusively
  human."
  [proposal]
  (when (contains? blocked-ops (:op proposal))
    [{:rule :equipment-or-certification-blocked
      :detail (str (:op proposal) " は印刷機の直接操作、色管理認証の確定、または品質検査の合否確定であり、恒久的にブロックされる -- 印刷技術者/品質管理責任者の専権事項")}]))

(defn- unknown-op-violations
  "Enforce the closed proposal-op allowlist independently of the
  advisor's claim -- an op outside `all-recognized-ops` is a HARD
  violation, never a silent pass-through."
  [proposal]
  (when-not (contains? all-recognized-ops (:op proposal))
    [{:rule :op-not-allowed
      :detail (str (:op proposal) " はクローズドallowlist外の操作")}]))

(defn- production-record-invalid-violations
  "For `:log-production-record`, INDEPENDENTLY verify the logged quantity
  is a plausible positive observation via
  `registry/quantity-non-positive?`. Evaluated only when a `:quantity` is
  present on the proposal."
  [proposal]
  (when (and (= :log-production-record (:op proposal))
             (contains? proposal :quantity)
             (registry/quantity-non-positive? (:quantity proposal)))
    [{:rule :production-record-invalid
      :detail (str "生産数量 " (:quantity proposal) " は正の数でなければならない -- 記録提案は進められない")}]))

(defn- quality-grade-invalid-violations
  "For `:log-production-record` and `:log-quality-inspection-record`,
  INDEPENDENTLY verify a logged quality-disposition grade is one of the
  actor's recognized closed vocabulary (`printing.facts/quality-grades`)
  via `registry/quality-grade-unknown?`. Evaluated only when a
  `:quality-grade` is present on the proposal."
  [proposal]
  (when (and (contains? #{:log-production-record :log-quality-inspection-record}
                        (:op proposal))
             (contains? proposal :quality-grade)
             (registry/quality-grade-unknown? (:quality-grade proposal)))
    [{:rule :quality-grade-invalid
      :detail (str "品質判定グレード（quality-grade） " (pr-str (:quality-grade proposal)) " は認識済みの判定コードではない -- 記録提案は進められない")}]))

(defn- print-spec-scope-violations
  "For any proposal citing a `:print-spec-id`, INDEPENDENTLY verify that
  spec is within the TARGET PRESS LINE's own verified-print-specs scope
  (never the proposal's own claim) via `registry/print-spec-verified?`.
  README: 'a print run cannot be released outside its verified color/
  specification scope' -- this check also gates production logging, since
  the operator guide requires 'color/print-specification-scope validation
  before any production run'."
  [{:keys [press-line-id]} proposal st]
  (when-let [spec-id (:print-spec-id proposal)]
    (let [press-line (store/registered-press-line st press-line-id)]
      (when-not (registry/print-spec-verified? press-line spec-id)
        [{:rule :print-spec-scope-violation
          :detail (str "print-spec-id " (pr-str spec-id) " は press-line " (pr-str press-line-id) " の検証済み色/仕様スコープに含まれていない -- 提案は進められない")}]))))

(defn- release-without-inspection-violations
  "`:release-print-run` INDEPENDENTLY re-derives the run's quality-
  inspection status from the Store's own index (never the proposal's
  self-report) -- a nil, unrecognized, or non-passing status is a HARD
  violation. README: 'the governor never releases a print run for
  delivery itself; ... a delivery release without a completed quality-
  inspection pass ... require[s] human sign-off'."
  [{:keys [press-line-id]} proposal st]
  (when (= :release-print-run (:op proposal))
    (let [run-id (:run-id proposal)
          status (store/quality-inspection-status st press-line-id run-id)]
      (when-not (and status (registry/passing-quality-grade? status))
        [{:rule :release-without-quality-inspection
          :detail (str "run-id " (pr-str run-id) " について press-line " (pr-str press-line-id) " の合格済み品質検査記録が確認できない -- 出荷リリースは進められない")}]))))

(defn- cost-threshold-for
  "Resolve the escalation threshold for a supply-order proposal: the
  category-specific threshold from `printing.facts` if the category is
  known, else the conservative default."
  [proposal]
  (let [category (get-in proposal [:value :category])
        c (and category (facts/supply-category-by-id category))]
    (or (:cost-threshold c) facts/default-cost-threshold)))

(defn check
  "Censors a PrintingOpsAdvisor proposal against the Governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (press-line-violations request st)
                           (execution-violations proposal)
                           (equipment-or-certification-violations proposal)
                           (unknown-op-violations proposal)
                           (production-record-invalid-violations proposal)
                           (quality-grade-invalid-violations proposal)
                           (print-spec-scope-violations request proposal st)
                           (release-without-inspection-violations request proposal st)))
        conf (:confidence proposal 0.0)
        low? (registry/confidence-below-floor? conf confidence-floor)
        cost (:cost proposal)
        high-cost? (boolean (and cost (registry/cost-exceeds-threshold?
                                        cost (cost-threshold-for proposal))))
        always-escalate? (contains? always-escalate-ops (:op proposal))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not high-cost?) (not always-escalate?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? high-cost? always-escalate?))
     :high-stakes? (boolean (or high-cost? always-escalate?))}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:press-line-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
