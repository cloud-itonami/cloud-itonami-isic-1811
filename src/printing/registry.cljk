(ns printing.registry
  "Pure validation functions for printing-plant operations. These are
  called by the Governor to independently verify proposal parameters --
  the LLM advisor's confidence is NOT sufficient to override these checks.
  Mirrors `tobaccoops.registry` (cloud-itonami-isic-0115) in shape, adding
  a `passing-quality-grade?` check (this domain's own release-gating
  measure: a print-run release proposal cites a quality-inspection status
  on file, and that status must be one of the actor's recognized passing
  outcomes -- see `printing.facts/passing-quality-grades`)."
  (:require [printing.facts :as facts]))

(defn cost-exceeds-threshold?
  "Independently verify a proposed spend against its category/default
  threshold. Inclusive at the boundary (exactly-at-threshold does not
  escalate)."
  [cost threshold]
  (> cost threshold))

(defn quantity-non-positive?
  "A logged production-run quantity of zero or negative is not a real
  observation -- reject it as a HARD violation rather than silently
  accepting bad data into the production record."
  [quantity]
  (<= quantity 0))

(defn quality-grade-unknown?
  "A logged quality-disposition grade that isn't in the actor's
  recognized closed vocabulary (`printing.facts/quality-grades`) is not a
  plausible observation -- reject it as a HARD violation (mirrors
  `tobaccoops.registry/leaf-grade-unknown?`: an independent structural
  plausibility check on a domain-specific field, not a colorimetric
  judgment about the run's actual quality)."
  [grade]
  (not (contains? facts/quality-grades grade)))

(defn passing-quality-grade?
  "Independently verify a quality-inspection status on file is one of the
  actor's recognized PASSING outcomes (`printing.facts/passing-quality-
  grades`). Used by the Governor's release-without-quality-inspection
  hard check -- a nil, unrecognized, or non-passing status never clears
  a `:release-print-run` proposal."
  [grade]
  (contains? facts/passing-quality-grades grade))

(defn print-spec-verified?
  "Independently verify a print-spec-id is within a press-line's own
  verified-print-specs scope (README: 'a print run cannot be released
  outside its verified color/specification scope'). `press-line` is the
  press-line record fetched from the Store (never the proposal's own
  self-report)."
  [press-line print-spec-id]
  (contains? (:verified-print-specs press-line #{}) print-spec-id))

(defn confidence-below-floor?
  "Independently verify a proposal's stated confidence against the
  Governor's confidence floor."
  [confidence floor]
  (< confidence floor))
