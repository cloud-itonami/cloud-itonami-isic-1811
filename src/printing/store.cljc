(ns printing.store
  "SSoT for the printing-plant operations coordinator, behind a `Store`
  protocol so the backend is a swap, not a rewrite -- the same seam every
  cloud-itonami actor in this fleet uses (mirrors `tobaccoops.store`,
  cloud-itonami-isic-0115):

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/printing/store_contract_test.cljc).

  FIX (deferred-stub bug + fabrication removed): a prior attempt at this
  repo hand-rolled a plain `{:data (atom {...})}` map with no protocol, no
  append-only audit ledger at all, and no quality-inspection index -- its
  demo data cited fabricated jurisdiction/regulatory fields
  (`:jurisdiction :JPN` etc.) that belonged to the fabricated
  `printing.facts` catalog this fix also removes. Replaced with the same
  Store-protocol discipline every sibling actor uses, plus this domain's
  own quality-inspection index (below).

  A registered press-line is the minimal unit of authority: a press
  line must be registered before ANY proposal referencing it can be
  considered by the Governor (see `printing.governor`'s
  `press-line-not-registered` invariant). Press-line data is opaque to
  this namespace -- callers/backends decide what a press-line record
  contains (name, press-type, verified-print-specs, etc); this Store only
  answers 'is this press-line-id registered, and if so what's on file'.
  Because the payload shape is intentionally open, `DatomicStore` stores
  it as a single opaque EDN-blob attribute (`:press-line/payload`, via
  `langchain-store.core`'s `enc`/`dec*`) rather than expanding it into
  per-key Datomic attributes -- the same blob convention every sibling
  DatomicStore already uses for its own opaque payloads.

  The quality-inspection index (`quality-inspection-status`/
  `record-quality-inspection!`) is this domain's own cross-op grounding:
  `printing.operation`'s `:commit` node writes it whenever a
  `:log-quality-inspection-record` proposal genuinely commits, and
  `printing.governor`'s `release-without-quality-inspection` hard check
  re-derives a run's inspection status from HERE -- never from a
  `:release-print-run` proposal's own self-report -- before a print run
  may even be considered for release (README: 'a delivery release without
  a completed quality-inspection pass ... require[s] human sign-off').

  The append-only audit ledger (`ledger`/`append-ledger!`) works exactly
  like every sibling actor's: `printing.operation`'s `:commit`/`:hold`
  graph nodes append every committed/held/approval-rejected decision fact
  here, so a press line's operating history is always a query over an
  immutable log. The ledger stays append-only on every backend."
  (:require [langchain.db :as d]
            [langchain-store.core :as ls]))

(defprotocol Store
  (registered-press-line [store press-line-id]
    "Retrieve a registered press-line record by ID. Returns nil if the
    press-line-id is nil or not registered.")
  (add-press-line [store press-line-id press-line-data]
    "Register or update a press line in the store. Used by tests,
    simulation, and operator onboarding.")
  (quality-inspection-status [store press-line-id run-id]
    "The last recorded quality-inspection grade for a press-line+run, or
    nil if no quality-inspection record is on file.")
  (record-quality-inspection! [store press-line-id run-id grade]
    "Record/update the quality-inspection status for a press-line+run.
    Returns grade.")
  (ledger [store]
    "The append-only audit ledger: every committed/held/approval-rejected
    decision fact, in append order.")
  (append-ledger! [store fact]
    "Append one immutable decision fact to the ledger. Returns fact."))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [press-lines inspections ledger-atom]
  Store
  (registered-press-line [_store press-line-id]
    (when press-line-id
      (get @press-lines press-line-id)))
  (add-press-line [_store press-line-id press-line-data]
    (swap! press-lines assoc press-line-id press-line-data)
    press-line-data)
  (quality-inspection-status [_store press-line-id run-id]
    (get @inspections [press-line-id run-id]))
  (record-quality-inspection! [_store press-line-id run-id grade]
    (swap! inspections assoc [press-line-id run-id] grade)
    grade)
  (ledger [_store] @ledger-atom)
  (append-ledger! [_store fact]
    (swap! ledger-atom conj fact)
    fact))

(defn mem-store
  "Create an in-memory store. `initial-press-lines` is an optional map of
  press-line-id -> press-line-record."
  [& [{:keys [initial-press-lines] :or {initial-press-lines {}}}]]
  (MemStore. (atom initial-press-lines) (atom {}) (atom [])))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  `:press-line/payload` is stored as an EDN string blob (via
  `langchain-store.core`) so `langchain.db` doesn't try to expand an
  opaque, caller-defined press-line record into sub-entities.
  `:inspection/key` is a composite `press-line-id|run-id` string identity
  attr so re-recording a run's inspection status upserts rather than
  forking history (mirrors `:field/id`'s upsert semantics in
  `tobaccoops.store`). The identity-schema builder, EDN-blob codec and
  seq-keyed event-log read/append are the shared kotoba-lang/
  langchain-store machinery (ADR-2607141600) -- the seam ~190 actors
  hand-roll; this store keeps only its domain wiring."
  (ls/identity-schema [:press-line/id :inspection/key :ledger/seq]))

(defn- inspection-key [press-line-id run-id]
  (str press-line-id "|" run-id))

(defrecord DatomicStore [conn]
  Store
  (registered-press-line [_store press-line-id]
    (when press-line-id
      (ls/dec* (d/q '[:find ?p .
                      :in $ ?pid
                      :where [?e :press-line/id ?pid] [?e :press-line/payload ?p]]
                    (d/db conn) press-line-id))))
  (add-press-line [_store press-line-id press-line-data]
    (d/transact! conn [{:press-line/id press-line-id
                        :press-line/payload (ls/enc press-line-data)}])
    press-line-data)
  (quality-inspection-status [_store press-line-id run-id]
    (d/q '[:find ?s .
           :in $ ?k
           :where [?e :inspection/key ?k] [?e :inspection/status ?s]]
         (d/db conn) (inspection-key press-line-id run-id)))
  (record-quality-inspection! [_store press-line-id run-id grade]
    (d/transact! conn [{:inspection/key (inspection-key press-line-id run-id)
                        :inspection/status grade}])
    grade)
  (ledger [_store] (ls/read-stream conn :ledger/seq :ledger/fact))
  (append-ledger! [store fact]
    (ls/append-blob! conn :ledger/seq :ledger/fact (count (ledger store)) fact)
    fact))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `initial-press-lines`
  (press-line-id -> press-line-record); empty when omitted."
  [& [{:keys [initial-press-lines] :or {initial-press-lines {}}}]]
  (let [s (->DatomicStore (d/create-conn schema))]
    (doseq [[press-line-id press-line-data] initial-press-lines]
      (add-press-line s press-line-id press-line-data))
    s))
