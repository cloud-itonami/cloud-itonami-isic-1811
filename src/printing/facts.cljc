(ns printing.facts
  "Per-jurisdiction printing plant safety and quality requirements.
  Every jurisdiction in this catalog is backed by an official spec-basis.
  NEVER invent requirements without an official citation.

  This is deliberately a starting catalog (honest coverage reporting) to
  prove the governor contract end-to-end, not a claim of global coverage.
  Adding a jurisdiction is additive: one map entry citing a real official
  source -- never fabricate a jurisdiction's requirements to make coverage
  look bigger.")

;; ----------------------------- jurisdiction catalog -----------------------------

(def catalog
  "Per-jurisdiction printing plant safety and quality requirements with official spec-basis citations."
  {
   :JPN
   {:name "Japan"
    :requirements
    {:worker-safety {:description "Occupational health and safety program for printing plant workers"
                     :required true
                     :spec-basis "Occupational Safety and Health Act (労働安全衛生法) §20"
                     :evidence [:safety-plan :worker-training-records :hazard-assessment]}
     :quality-assurance {:description "Print quality and color accuracy verification procedures"
                         :required true
                         :spec-basis "Product Liability Law (製造物責任法) §3"
                         :evidence [:quality-inspection-procedure :color-profile-verification :customer-spec]}
     :equipment-maintenance {:description "Regular maintenance and calibration of printing equipment"
                            :required true
                            :spec-basis "Occupational Safety and Health Act (労働安全衛生法) §30"
                            :evidence [:maintenance-schedule :equipment-inspection-log :calibration-cert]}
     :chemical-handling {:description "Ink, solvent, and chemical hazard management"
                        :required true
                        :spec-basis "Industrial Safety and Health Act §21"
                        :evidence [:msds-sheets :chemical-storage-cert :disposal-plan]}}}

   :USA
   {:name "United States"
    :requirements
    {:worker-safety {:description "OSHA-compliant occupational safety program"
                     :required true
                     :spec-basis "OSHA 1910 Subpart Z (Hazardous Substances)"
                     :evidence [:safety-plan :incident-log :training-records]}
     :quality-assurance {:description "Print quality standards and color management"
                        :required true
                        :spec-basis "ISO 12647-2 (Graphic technology — Process control)"
                        :evidence [:quality-control-procedure :color-accuracy-log]}
     :environmental-compliance {:description "EPA compliance for printing press emissions and waste"
                               :required true
                               :spec-basis "Clean Air Act (42 USC §7661) and Resource Conservation and Recovery Act (RCRA)"
                               :evidence [:emissions-baseline :waste-disposal-plan :environmental-permit]}
     :chemical-safety {:description "HAZCOM compliance for ink and solvent storage"
                      :required true
                      :spec-basis "OSHA 1910.1200 (Hazard Communication)"
                      :evidence [:msds-library :chemical-inventory :labeling-cert]}}}

   :GBR
   {:name "United Kingdom"
    :requirements
    {:worker-safety {:description "Health and Safety at Work Act compliance"
                     :required true
                     :spec-basis "Health and Safety at Work etc. Act 1974 (HSWA)"
                     :evidence [:risk-assessment :safety-instruction :incident-reporting]}
     :quality-assurance {:description "Print quality and color accuracy requirements"
                        :required true
                        :spec-basis "British Standard BS ISO 12647-2:2013"
                        :evidence [:quality-procedure :color-management-system]}
     :environmental-compliance {:description "Environmental Permitting for emissions and waste"
                               :required true
                               :spec-basis "Environmental Permitting (England and Wales) Regulations 2016"
                               :evidence [:environmental-permit :waste-management-plan]}}}})

;; ----------------------------- coverage reporting (honest) -----------------------------

(defn coverage
  "Report what fraction of worldwide jurisdictions have official spec-basis
  in this catalog. Honest about out-of-scope coverage."
  []
  (let [catalog-count (count catalog)
        world-jurisdictions 194]
    {:implemented catalog-count
     :worldwide-jurisdictions world-jurisdictions
     :coverage-pct (* 100.0 (/ catalog-count world-jurisdictions))
     :note "Starting catalog to prove governor contract end-to-end, not global coverage claim"}))

;; ----------------------------- helpers -----------------------------

(defn requirement-citations
  "Get all official citations for a jurisdiction's requirements."
  [jurisdiction]
  (get-in catalog [jurisdiction :requirements]))

(defn required-evidence-satisfied?
  "Check if a checklist satisfies this jurisdiction's evidence requirements."
  [jurisdiction checklist]
  (let [reqs (get-in catalog [jurisdiction :requirements])]
    (every? (fn [[_req-key req-spec]]
              (if (:required req-spec)
                (let [evidence-keys (set (:evidence req-spec))]
                  (every? #(contains? checklist %) evidence-keys))
                true))
            reqs)))
