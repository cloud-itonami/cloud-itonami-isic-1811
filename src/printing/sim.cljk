(ns printing.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a registered press line
  through a clean phase-3 auto-commit, an always-escalate quality concern
  (human approves), a high-cost supply order (human rejects), a hard hold
  (unregistered press line), a quality-inspection record that commits and
  updates the Store's quality-inspection index, a release-print-run that
  re-derives that index and escalates for human sign-off (approved), and
  a release-print-run for a DIFFERENT run with no inspection on file
  (hard hold) -- then prints the resulting audit ledger. Mirrors
  `tobaccoops.sim` (cloud-itonami-isic-0115).

  FIX (deferred-stub bug): a prior attempt at this repo's `printing.sim`
  was a bare `(println \"Printing Operations Coordinator Demo\")` -- it
  never called the advisor, governor, or store, so `clojure -M:dev:run`
  proved nothing about the actor's actual behavior."
  (:require [langgraph.graph :as g]
            [printing.operation :as operation]
            [printing.store :as store]))

(def printer {:actor-id "printing-ops-01" :role :press-operator :phase :phase-3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "printing-ops-01"}}
          {:thread-id tid :resume? true}))

(defn- reject! [actor tid]
  (g/run* actor {:approval {:status :rejected :by "printing-ops-01"}}
          {:thread-id tid :resume? true}))

(defn demo
  "Run the compiled StateGraph through a commit path, an
  escalate->approve->commit path, an escalate->reject->hold path, a hard-
  hold path, and a quality-inspection-gated release path (both the
  inspection-on-file success and the inspection-missing hard hold);
  print each result and the final audit ledger."
  []
  (let [st (store/mem-store
            {:initial-press-lines
             {"press-001"
              {:id "press-001"
               :name "Line 1 (Offset)"
               :press-type "offset"
               :verified-print-specs #{"spec-catalog-2026a"}}}})
        actor (operation/build st)]

    (println "=== Printing Operations Coordinator Demo ===")

    (println "\n== log-production-record press-001 (phase-3, governor-clean -> commit) ==")
    (println (exec-op actor "t1"
                      {:op :log-production-record :press-line-id "press-001"
                       :quantity 5000 :press-type "offset" :quality-grade "pass"
                       :print-spec-id "spec-catalog-2026a" :run-id "run-1001"}
                      printer))

    (println "\n== flag-quality-concern press-001 (ALWAYS escalates -- printer/quality-manager approves) ==")
    (let [r (exec-op actor "t2"
                     {:op :flag-quality-concern :press-line-id "press-001"
                      :concern "スポットカラーのドリフトの可能性"}
                     printer)]
      (println r)
      (println "-- printer/quality-manager approves --")
      (println (approve! actor "t2")))

    (println "\n== order-supplies press-001 over cost threshold (escalates -- printer rejects) ==")
    (let [r (exec-op actor "t3"
                     {:op :order-supplies :press-line-id "press-001"
                      :category "plates" :cost 1800}
                     printer)]
      (println r)
      (println "-- printer rejects --")
      (println (reject! actor "t3")))

    (println "\n== log-production-record press-999 (unregistered -> HARD hold, no interrupt) ==")
    (println (exec-op actor "t4"
                      {:op :log-production-record :press-line-id "press-999"
                       :quantity 2000 :press-type "digital"}
                      printer))

    (println "\n== log-quality-inspection-record press-001 run-1001 (governor-clean -> commit, updates quality-inspection index) ==")
    (println (exec-op actor "t5"
                      {:op :log-quality-inspection-record :press-line-id "press-001"
                       :run-id "run-1001" :quality-grade "pass" :inspector "qc-01"}
                      printer))

    (println "\n== release-print-run press-001 run-1001 (ALWAYS escalates, but hard checks pass -- inspection on file, spec verified -- printer approves) ==")
    (let [r (exec-op actor "t6"
                     {:op :release-print-run :press-line-id "press-001"
                      :run-id "run-1001" :print-spec-id "spec-catalog-2026a"}
                     printer)]
      (println r)
      (println "-- printer approves --")
      (println (approve! actor "t6")))

    (println "\n== release-print-run press-001 run-9999 (no quality-inspection record on file -> HARD hold, no interrupt) ==")
    (println (exec-op actor "t7"
                      {:op :release-print-run :press-line-id "press-001"
                       :run-id "run-9999" :print-spec-id "spec-catalog-2026a"}
                      printer))

    (println "\n== audit ledger ==")
    (doseq [f (store/ledger st)] (println f))

    {:ledger (store/ledger st)}))

(defn -main
  "clojure -M:run entrypoint."
  [& _args]
  (demo))

(comment
  ;; In a real REPL:
  (demo)
  )
