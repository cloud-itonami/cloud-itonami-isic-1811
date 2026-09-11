(ns printing.facts
  "Reference facts for printing-plant operations coordination: supply
  category cost policy, press-type classification, press/finishing
  operation vocabulary, and a closed print-run quality-disposition
  vocabulary. This namespace contains pure lookup functions for domain
  reference data -- the Governor and Advisor consult these instead of
  inventing thresholds. Mirrors `tobaccoops.facts` (cloud-itonami-isic-0115)
  in shape, adapted to commercial press production: color-managed offset,
  digital and flexographic print runs with finishing and binding (README's
  scope note), rather than a leaf crop.

  FIX (fabrication removed): a prior attempt at this repo used this
  namespace for a `catalog` of per-jurisdiction regulatory citations
  (JPN/USA/GBR) inventing specific statute sections that are NOT present
  anywhere in this repo's own README/docs (e.g. '労働基準法 §36', 'OSHA
  1910 Subpart Z', 'Product Liability Law §3', '42 USC §7661', 'HSWA
  1974', 'BS ISO 12647-2:2013' formal titles/subsections). That is a
  zero-fabrication violation: this actor is an INTERNAL OPERATIONS
  coordinator, not a jurisdiction-facts actor, and README/docs/business-
  model.md only ever mention ISO 12647 / G7 Master Certification / OSHA
  machine-guarding / EPA+EU industrial-emissions frameworks / FSC
  chain-of-custody as a single descriptive sentence, with no
  section-level citations to verify. This namespace is now purely
  structural/internal (cost thresholds, press-type vocabulary, operation
  vocabulary, quality vocabulary) per the domain-fact caution: keep the
  governor's hard-checks structural, do not add new unverified
  regulatory/standards claims.")

(def supply-categories
  "Procurement categories this actor may propose orders for, and the
  default cost threshold above which an order proposal must escalate for
  human sign-off (press operator/ops-manager). Printing plates are this
  domain's distinctive high-cost supply category (offset plate-imaging
  runs cost materially more per order than routine ink/substrate
  restocking), priced above routine ink/substrate inputs -- mirrors
  tobacco-growing's curing-fuel threshold in shape."
  {"ink"
   {:id "ink" :name "インキ" :cost-threshold 500}

   "substrate"
   {:id "substrate" :name "用紙・基材" :cost-threshold 500}

   "plates"
   {:id "plates" :name "刷版" :cost-threshold 1500}})

(defn supply-category-by-id [id]
  (get supply-categories id))

(def default-cost-threshold
  "Fallback escalation threshold used when a supply-order proposal doesn't
  cite a known category (never invent a lower bar than this)."
  500)

(def press-types
  "Press types this actor's production records may cover (ISIC 1811:
  commercial press production -- color-managed offset, digital and
  flexographic print runs, with finishing and binding, per this repo's
  own README scope note). Other printing-adjacent activity (independent
  pre-press/post-press service work) is out of scope -- see
  cloud-itonami-isic-1812."
  {"offset"       {:id "offset" :name "オフセット印刷"}
   "digital"      {:id "digital" :name "デジタル印刷"}
   "flexographic" {:id "flexographic" :name "フレキソ印刷"}})

(defn press-type-by-id [id]
  (get press-types id))

(def press-operation-types
  "Reference set of press/finishing-operation types this actor's
  schedule-press-operation proposals commonly cover: robotics-assisted
  press-feed, in-line quality-inspection, finishing/binding, and
  press-setup/color-calibration (this repo's README capability layer
  cites `kotoba-lang/cae` for 'press-setup/color-calibration simulation
  evidence'). Informational only -- NOT a validated enum; the
  advisor/operator may propose other operation-type strings and the
  Governor does not reject unlisted values here."
  #{"press-feed" "quality-inspection" "finishing" "binding" "color-calibration"})

(def quality-grades
  "Closed set of recognized print-run quality-disposition codes a
  production or quality-inspection record's :quality-grade may cite --
  independently verified by the Governor. Generic closed vocabulary this
  actor's records use to record a graded outcome, not a physical
  colorimetric measurement or a substitute for a color-management
  standard."
  #{"pass" "conditional-pass" "fail" "rework-required" "ungraded"})

(def passing-quality-grades
  "The subset of `quality-grades` that counts as a completed, passing
  quality-inspection outcome -- consulted by the Governor's
  release-without-quality-inspection hard check (README: 'the governor
  never releases a print run for delivery itself; ... a delivery release
  without a completed quality-inspection pass ... require[s] human
  sign-off')."
  #{"pass" "conditional-pass"})
