(ns printing.advisor
  "Printing Operations Advisor -- the LLM-driven suggestion layer.
  Proposes operations to the Governor for approval.")

;; ----------------------------- mock advisor for testing -----------------------------

(defn mock-advisor
  "Create a mock advisor for testing. Real implementation would call an LLM."
  []
  {:type :mock :model "mock-v1"})

(defn log-production-batch
  "Propose logging a production batch (routine job output recording)."
  [_advisor batch-id]
  {:op :proposal/log-production-batch
   :subject batch-id
   :effect :propose
   :cites ["Labor Standards Act (労働基準法) §36"]
   :value {:evidence {:batch-registered true :job-specification true :material-verified true}
           :confidence 0.87
           :detail "Production batch logged and verified for output"}})

(defn schedule-maintenance
  "Propose equipment maintenance scheduling."
  [_advisor equipment-id]
  {:op :proposal/schedule-maintenance
   :subject equipment-id
   :effect :propose
   :cites ["Occupational Safety and Health Act (労働安全衛生法) §20"]
   :value {:evidence {:maintenance-schedule true :technician-available true}
           :confidence 0.82
           :detail "Equipment maintenance scheduled within service intervals"}})

(defn flag-quality-defect
  "Propose flagging a quality/color-accuracy issue -- ALWAYS escalates."
  [_advisor job-id]
  {:op :actuation/flag-quality-defect
   :subject job-id
   :effect :propose
   :cites ["Product Liability Law (製造物責任法) §3"]
   :value {:evidence {:quality-inspection-report true :defect-photo true}
           :confidence 0.90
           :has-quality-issue? true
           :detail "Color accuracy or print quality defect detected, requires review"}})

(defn coordinate-shipment
  "Propose outbound product shipment coordination."
  [_advisor shipment-id]
  {:op :proposal/coordinate-shipment
   :subject shipment-id
   :effect :propose
   :cites ["Transport Safety Regulation (運輸安全管理規則)"]
   :value {:evidence {:quality-final-check true :packing-certified true :carrier-confirmed true}
           :confidence 0.88
           :detail "Product batch ready for shipment coordination"}})

(defn schedule-production-batch
  "Propose scheduling a production batch (real actuation requiring sign-off)."
  [_advisor batch-id]
  {:op :actuation/schedule-production-batch
   :subject batch-id
   :effect :propose
   :cites ["Occupational Safety and Health Act (労働安全衛生法) §20"]
   :value {:evidence {:batch-verified true :materials-prepared true :job-instructions true}
           :confidence 0.85
           :detail "Production batch ready to schedule for press run"}})
