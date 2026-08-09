(ns printing.prepress
  "Prepress craft ops that *consume* `seihan.core/plan` results.

  Paper commercial printing (ISIC 1811) — NOT garment white-underbase
  (that is `shirohan` / finishingops). This actor never invents plate
  geometry. It calls the pure paper-prepress engine once, then stores
  only:

    - input content hash (canonical job map)
    - plan summary (plate labels / imposition tallies / finding counts)

  Full plate films / RIP geometry stay out of the SSoT — recompute from
  the same job + hash when a film is needed. Governor HARD-holds any plan
  whose findings include a blocking kind (`seihan.core/blocking?`).

  Ops (closed allowlist in `printing.governor`):

    :prepress/plan            — job map → seihan/plan → summary proposal
    :prepress/approve-plates  — human plate approval (always escalate)

  Sibling pattern: `finishingops.prepress` (shirohan / ISIC 1313)."
  (:require [clojure.string :as str]
            [seihan.core :as seihan])
  #?(:clj (:import (java.security MessageDigest)
                   (java.nio.charset StandardCharsets))))

;; ---------------------------------------------------------------- hash

(defn content-hash
  "SHA-256 hex of the UTF-8 pr-str of the input job map.

  Identity of a plan is identity of its input: seihan is pure, so the
  same job always yields the same plates. We store this hash rather than
  re-serializing geometry."
  [job]
  (let [s (pr-str job)]
    #?(:clj
       (let [md (MessageDigest/getInstance "SHA-256")
             bs (.digest md (.getBytes (str s) StandardCharsets/UTF_8))]
         (apply str (map #(format "%02x" (bit-and % 0xff)) bs)))
       :cljs
       ;; Browser path not exercised by JVM tests; keep a stable pure fallback
       ;; so the ns loads. Prefer SubtleCrypto at the cljs host boundary.
       (str "cljs-" (count s) "-" (hash s)))))

;; ---------------------------------------------------------------- resolve job

(def ^:private job-keys
  [:pages :colors :paper-mm :bleed-mm :trap-mm :binding :press-sheet-mm])

(defn resolve-job
  "Pull a seihan job map from a request `:value` (or the request itself).

  Accepts:
    :job       — nested job map (preferred)
    top-level  — :pages / :colors / :paper-mm / … on the same map

  Returns nil when nothing job-shaped is present (empty value, no keys)."
  [value]
  (let [v (or value {})]
    (cond
      (map? (:job v))
      (select-keys (:job v) job-keys)

      (some #(contains? v %) job-keys)
      (select-keys v job-keys)

      :else nil)))

;; ---------------------------------------------------------------- plan → summary (no geometry)

(defn plan-summary
  "Reduce a seihan job to the audit/SSoT summary.

  Never invents plate geometry — only labels / kind / order / trap-mm from
  `seihan/summary`, plus the input hash and finding tallies."
  [job-input engine-job]
  (let [sum (seihan/summary engine-job)
        findings (:findings engine-job [])
        blocking-fs (filterv seihan/blocking? findings)
        ih (content-hash job-input)]
    {:input-hash     ih
     :digest         ih
     :ok?            (boolean (:ok? sum))
     :blocking       (count blocking-fs)
     :findings-count (count findings)
     :finding-kinds  (mapv :kind findings)
     :blocking-kinds (mapv :kind blocking-fs)
     :plate-count    (:plate-count sum)
     :plate-labels   (mapv :label (:plates sum))
     ;; summary rows only (id/label/kind/order/trap-mm) — no contours/films
     :plates         (:plates sum)
     :pages          (:pages sum)
     :paper-mm       (:paper-mm sum)
     :bleed-mm       (:bleed-mm sum)
     :trap-mm        (:trap-mm sum)
     :binding        (:binding sum)
     :imposition     (:imposition sum)
     :spec           (select-keys (or (:spec engine-job) {})
                                  [:pages :colors :paper-mm :bleed-mm :trap-mm
                                   :binding :press-sheet-mm])
     :source         :seihan}))

(defn run-plan
  "Call `seihan.core/plan` and return `{:summary .. :job ..}` or
  `{:error :job-missing}` when no job map can be resolved.

  The full `:job` is returned only for the advisor's one-shot use; the
  commit path must take `:summary` alone (no inventing geometry on the
  ledger)."
  [value]
  (if-let [job (resolve-job value)]
    (let [engine-job (seihan/plan job)]
      {:job-input job :job engine-job :summary (plan-summary job engine-job)})
    {:error :job-missing}))

(defn printable?
  "True only when blocking finding count is zero and an input hash is set."
  [summary]
  (and (map? summary)
       (number? (:blocking summary))
       (zero? (:blocking summary))
       (string? (:input-hash summary))
       (seq (:input-hash summary))))

(defn blocking-findings?
  [summary]
  (and (map? summary)
       (number? (:blocking summary))
       (pos? (:blocking summary))))

(defn subject-of
  "Prepress job id from a request. Prefers `:subject`, falls back to
  `:press-line-id` so legacy press-line-shaped callers still work."
  [request]
  (or (:subject request) (:press-line-id request)))
