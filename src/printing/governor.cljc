(ns printing.governor
  "Printing Plant Operations Governor -- the independent compliance layer that
  earns the Printing Operations Advisor the right to propose and log actions.
  The LLM has no notion of print-plant quality standards, workplace safety,
  equipment maintenance protocols, or when job scheduling is a real-world actuation,
  so this MUST be a separate system able to *reject* a proposal and fall back
  to HOLD.

  HARD violations (a human approver CANNOT override):
    1. Spec-basis       -- no official jurisdiction citation
    2. Job registration -- production batch must be verified/registered
    3. Quality escalation -- quality defects ALWAYS escalate (never silent log)
    4. Equipment control -- NO direct press/equipment control authority
                            (those remain plant engineer exclusive)

  SOFT violation (can be approved by human):
    5. Confidence floor / actuation gate -- low confidence OR real actuation

  CRITICAL SCOPE BOUNDARY:
  This actor coordinates LOGISTICS and COMPLIANCE PAPERWORK around the
  print shop operation. It does NOT:
    - Control press settings, ink systems, or equipment parameters
    - Decide plate/film imaging or color profile tuning
    - Operate finishing equipment (binding, folding, cutting -- monitors only)
    - Make process-engineering decisions about substrate/ink chemistry

  Those remain the exclusive authority of licensed print technicians and
  equipment operators."
  (:require [printing.store :as store]))

(def confidence-floor 0.65)

(def high-stakes
  "Operations that require human sign-off for real-world actuation:
  Production-batch scheduling and quality-defect escalations."
  #{:actuation/schedule-production-batch :actuation/flag-quality-defect})

(def equipment-control-keywords
  "Words that indicate direct equipment/press control authority (FORBIDDEN for this actor).
  If a proposal mentions any of these, it's a hard block."
  #{"press-control" "ink-adjustment" "pressure-setting" "temperature-control"
    "color-profile" "rgbk-adjustment" "dot-gain-adjustment" "equipment-operation"
    "press" "speed-control" "tension" "dampening"})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A proposal with no spec-basis citation is a HARD violation --
  never invent a jurisdiction's requirements."
  [proposal _st]
  (let [op (:op proposal)]
    (when (contains? #{:actuation/schedule-production-batch
                       :actuation/flag-quality-defect} op)
      (when (or (empty? (:cites proposal))
                (and (contains? (:value proposal) :spec-basis)
                     (nil? (:spec-basis (:value proposal)))))
        [{:rule :no-spec-basis
          :detail "公式な仕様基準の引用が無い提案は処理できない"}]))))

(defn- job-verification-violations
  "Production batch must be registered and verified before processing."
  [_proposal _st]
  ;; In production, would check that the job/batch is in the store
  ;; and marked as verified. For now, this is a soft gate.
  nil)

(defn- equipment-control-block-violations
  "HARD BLOCK: This actor does NOT make equipment-control decisions.
  If a proposal mentions press control, ink/color adjustment, or equipment
  parameters, reject it immediately. Those remain exclusive to qualified
  print technicians."
  [proposal _st]
  (let [detail (str (:detail (:value proposal)) " " (:op proposal))
        words (re-seq #"\w+" (.toLowerCase detail))
        forbidden (some #(contains? equipment-control-keywords %) words)]
    (when forbidden
      [{:rule :equipment-control-forbidden
        :detail (str "プレス操作は認可オペレータの排他的権限です。"
                    "この提案には禁止キーワード '" forbidden "' が含まれています。")}])))

(defn- quality-defect-escalation-violations
  "Quality defects MUST escalate to human. Never silently log a defect."
  [{:keys [op]} {:keys [has-quality-issue?]}]
  (when (= op :actuation/flag-quality-defect)
    (when has-quality-issue?
      [{:rule :quality-defect-escalation
        :detail "品質不良は必ず人間にエスカレートされる"}])))

(defn- batch-verification-violations
  "Production batch must be verified before scheduling."
  [{:keys [op subject]} st]
  (when (= op :actuation/schedule-production-batch)
    (when-not (store/batch-verified? st subject)
      [{:rule :batch-not-verified
        :detail "生産バッチが未検証"}])))

(defn- confidence-gate-violations
  "Low confidence or high-stakes actuation -> escalate to human."
  [{:keys [op]} {:keys [confidence]}]
  (let [confidence (or confidence 0.5)]
    (when (or (< confidence confidence-floor)
              (contains? high-stakes op))
      [{:rule :escalate
        :detail (if (< confidence confidence-floor)
                  (str "信頼度が低い (confidence=" confidence ")")
                  "実際の操作には人間の承認が必要")}])))

;; ----------------------------- governor evaluation -----------------------------

(defn evaluate
  "Evaluate a proposal against all hard and soft gates.
  Returns a map:
    {:holds? boolean
     :hard-violations [...]
     :soft-violations [...]
     :clean? boolean}"
  [proposal st]
  (let [hard-checks-store [spec-basis-violations
                           job-verification-violations
                           equipment-control-block-violations
                           batch-verification-violations]
        hard-checks-value [quality-defect-escalation-violations]
        soft-checks [confidence-gate-violations]
        hard-violations-store (mapcat #(% proposal st) hard-checks-store)
        hard-violations-value (mapcat #(% proposal (:value proposal)) hard-checks-value)
        hard-violations (concat hard-violations-store hard-violations-value)
        soft-violations (mapcat #(% proposal (:value proposal)) soft-checks)]
    {:holds? (seq hard-violations)
     :hard-violations (vec hard-violations)
     :soft-violations (vec soft-violations)
     :clean? (and (empty? hard-violations) (empty? soft-violations))}))
