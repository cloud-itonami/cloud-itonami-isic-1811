(ns printing.registry
  "Proposal registry and drafting helpers for printing operations.
  Every proposal carries its spec-basis and evidence checklist.")

;; ----------------------------- hard invariants -----------------------------

(defn hard-invariant-violations
  "Hard invariants that CANNOT be overridden:
  - If operation affects safety or quality, it must carry spec-basis."
  [op-type value]
  (when (contains? #{:actuation/schedule-production-batch :actuation/flag-quality-defect} op-type)
    (when (or (empty? (:cites value))
              (and (contains? value :spec-basis) (nil? (:spec-basis value))))
      [{:rule :no-spec-basis
        :detail "公式な仕様基準の引用が無い提案は処理できない"}])))

(defn protected-operation-violations
  "Operations that require human sign-off and can never be autonomous:
  - Production batch scheduling
  - Quality defect flagging"
  [op-type]
  (when (contains? #{:actuation/schedule-production-batch :actuation/flag-quality-defect} op-type)
    [{:rule :requires-human-approval
      :detail "本製造工程の提案・実行には人間の承認が必須"}]))

;; ----------------------------- proposal drafts -----------------------------

(defn log-production-batch-draft
  "Draft a routine production batch logging proposal (no escalation required).
  subject: batch ID
  cites: spec-basis citations
  evidence-checklist: map of verified batch records"
  [subject cites evidence-checklist confidence detail]
  {:op :proposal/log-production-batch
   :subject subject
   :effect :propose
   :cites cites
   :value {:evidence evidence-checklist
           :confidence confidence
           :detail detail}})

(defn schedule-maintenance-draft
  "Draft an equipment maintenance scheduling proposal.
  subject: equipment ID
  cites: spec-basis citations
  evidence-checklist: map of maintenance schedule verification"
  [subject cites evidence-checklist confidence detail]
  {:op :proposal/schedule-maintenance
   :subject subject
   :effect :propose
   :cites cites
   :value {:evidence evidence-checklist
           :confidence confidence
           :detail detail}})

(defn flag-quality-defect-draft
  "Draft a quality defect flagging proposal (ALWAYS escalates).
  subject: job ID
  cites: spec-basis citations
  has-quality-issue?: boolean -- if true, this ALWAYS escalates to human
  evidence-checklist: map of quality inspection results"
  [subject cites has-quality-issue? evidence-checklist confidence detail]
  {:op :actuation/flag-quality-defect
   :subject subject
   :effect :propose
   :cites cites
   :value {:evidence evidence-checklist
           :confidence confidence
           :has-quality-issue? has-quality-issue?
           :detail detail}})

(defn coordinate-shipment-draft
  "Draft an outbound shipment coordination proposal.
  subject: shipment ID
  cites: spec-basis citations
  evidence-checklist: map of quality and readiness verification"
  [subject cites evidence-checklist confidence detail]
  {:op :proposal/coordinate-shipment
   :subject subject
   :effect :propose
   :cites cites
   :value {:evidence evidence-checklist
           :confidence confidence
           :detail detail}})

(defn schedule-production-batch-draft
  "Draft a production batch scheduling proposal (ALWAYS escalates).
  subject: batch ID
  cites: spec-basis citations
  evidence-checklist: map of verified batch and material records"
  [subject cites evidence-checklist confidence detail]
  {:op :actuation/schedule-production-batch
   :subject subject
   :effect :propose
   :cites cites
   :value {:evidence evidence-checklist
           :confidence confidence
           :detail detail}})
