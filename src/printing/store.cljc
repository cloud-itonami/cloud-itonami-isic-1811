(ns printing.store
  "In-memory store for printing plant operations state.
  This is a reference implementation; production systems would use Datomic
  or similar persistent event store for audit and replay.")

;; ----------------------------- store initialization -----------------------------

(defn mem-store
  "Create an in-memory store with reference data."
  []
  {:data (atom {
           :jobs {
             "job-001" {:customer "Client Corp A"
                       :description "Business card printing, full color"
                       :verified? true
                       :jurisdiction :JPN}
             "job-002" {:customer "Publisher Ltd"
                       :description "Book cover printing"
                       :verified? false
                       :jurisdiction :USA}}
           :batches {
             "batch-001" {:job "job-001"
                         :quantity 5000
                         :paper-type "glossy-cardstock"
                         :verified? true}
             "batch-002" {:job "job-002"
                         :quantity 1000
                         :paper-type "matte-cover"
                         :verified? false}}
           :equipment {
             "press-001" {:model "Heidelberg SX"
                         :status :operational
                         :last-maintenance "2026-07-01"
                         :next-scheduled "2026-08-01"}
             "press-002" {:model "Komori LS"
                         :status :operational
                         :last-maintenance "2026-06-15"
                         :next-scheduled "2026-07-20"}}
           :quality-checks {
             "qc-001" {:batch "batch-001"
                      :status :pass
                      :color-accuracy "within-tolerance"
                      :checked-at "2026-07-14T10:00:00Z"}
             "qc-002" {:batch "batch-002"
                      :status :flagged
                      :color-accuracy "out-of-tolerance"
                      :checked-at "2026-07-14T11:30:00Z"}}
           :shipments {
             "shipment-001" {:batch "batch-001"
                            :customer "Client Corp A"
                            :status :ready
                            :carrier "ShipCo Ltd"}}})})

;; ----------------------------- accessors -----------------------------

(defn job
  "Get job record by ID."
  [st job-id]
  (get-in @(:data st) [:jobs job-id]))

(defn batch
  "Get production batch record by ID."
  [st batch-id]
  (get-in @(:data st) [:batches batch-id]))

(defn equipment
  "Get equipment record by ID."
  [st equipment-id]
  (get-in @(:data st) [:equipment equipment-id]))

(defn quality-check
  "Get quality check record by ID."
  [st qc-id]
  (get-in @(:data st) [:quality-checks qc-id]))

(defn shipment
  "Get shipment record by ID."
  [st shipment-id]
  (get-in @(:data st) [:shipments shipment-id]))

;; ----------------------------- guards -----------------------------

(defn batch-verified?
  "Check if production batch is verified."
  [st batch-id]
  (let [b (batch st batch-id)]
    (:verified? b false)))

(defn equipment-operational?
  "Check if equipment is in operational status."
  [st equipment-id]
  (let [e (equipment st equipment-id)]
    (= (:status e) :operational)))

(defn quality-issue-exists?
  "Check if quality check shows defects."
  [st qc-id]
  (let [qc (quality-check st qc-id)]
    (or (= (:status qc) :flagged)
        (= (:color-accuracy qc) :out-of-tolerance))))

(defn shipment-ready?
  "Check if shipment is ready for dispatch."
  [st shipment-id]
  (let [s (shipment st shipment-id)]
    (= (:status s) :ready)))
