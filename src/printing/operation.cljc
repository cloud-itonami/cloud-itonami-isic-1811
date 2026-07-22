(ns printing.operation
  "OperationActor -- one printing-plant operation = one supervised
  actor run, expressed as a langgraph-clj StateGraph. The advisor
  (PrintingOpsAdvisor) is sealed into a single node (:advise); its
  proposal is ALWAYS routed through the Printing Governor (:govern) and
  the rollout phase gate (:decide) before anything commits to the SSoT.

  Everything the actor depends on is injected, so each is a swap, not a
  rewrite:
    - the Store    (MemStore | DatomicStore, see `printing.store`)
    - the Advisor  (mock today; real LLM is the next seam --
                     `printing.advisor/Advisor` is already the injection
                     point, see its docstring)
    - the Phase    (0->3 rollout)

  One graph run = one printing operations coordination operation. No
  unbounded inner loop -- each operation is auditable and checkpointed. A
  press line's operating history is advanced by MANY operations
  (log-production-record / log-quality-inspection-record /
  schedule-press-operation / flag-quality-concern / order-supplies /
  release-print-run), each its own independent graph run, and every
  commit/hold/approval-rejected decision fact lands in `printing.store`'s
  append-only ledger (`store/append-ledger!`), so a press line's full
  operating history is always a query over an immutable log.

  Human-in-the-loop = real approval workflow:
  `interrupt-before #{:request-approval}` pauses the actor at the
  `:request-approval` node until a human operator (printer/quality-
  control manager) resumes it with a decision. `:flag-quality-concern`
  and `:release-print-run` ALWAYS reach this node when the Governor is
  clean -- see `printing.governor/always-escalate-ops`. Mirrors
  `tobaccoops.operation` (cloud-itonami-isic-0115) node/edge structure
  exactly, wired to this repo's own advisor/governor/phase/store.

  A `:log-quality-inspection-record` proposal that genuinely reaches
  :commit ALSO updates the Store's quality-inspection index
  (`store/record-quality-inspection!`) -- the same durable fact
  `:release-print-run`'s hard `release-without-quality-inspection` check
  later re-derives, never from a proposal's own self-report.

  FIX (deferred-stub bug): this namespace did not exist at all in a prior
  attempt at this repo -- `printing.sim` was a bare `(println \"Printing
  Operations Coordinator Demo\")` that never called the advisor, governor,
  or store, and `printing.governor/evaluate` had no notion of a
  disposition, a commit path, or an audit ledger. `build` now returns a
  genuinely compiled `langgraph.graph/state-graph`, proven end-to-end by
  `test/printing/operation_test.cljc`."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [printing.advisor :as advisor]
            [printing.governor :as governor]
            [printing.phase :as phase]
            [printing.store :as store]))

(defn- commit-fact
  "The audit fact written when a proposal commits. `:record` carries the
  operational payload the advisor proposed (production/quality-inspection
  record, schedule, concern, supply order, release) -- printing has no
  separate stateful commit-record! entity beyond press-line registration,
  so the ledger fact itself is the durable record of what happened."
  [request context proposal]
  {:t          :committed
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:press-line-id request)
   :disposition :commit
   :basis      (:cites proposal)
   :summary    (:summary proposal)
   :record     (:value proposal)})

(defn- commit-record [request _context proposal]
  {:effect  (:effect proposal)
   :path    [(:press-line-id request)]
   :value   (or (:value proposal) {})
   :payload (:value proposal)})

(defn- record-quality-inspection-if-applicable!
  "When a `:log-quality-inspection-record` proposal reaches :commit, also
  update the press-line+run quality-inspection index this Store keeps --
  the same durable fact `:release-print-run`'s hard `release-without-
  quality-inspection` check later re-derives from the Store, never from a
  proposal's self-report (README: 'a print run cannot be released ...
  without a completed quality-inspection pass')."
  [store request proposal]
  (when (= :log-quality-inspection-record (:op request))
    (store/record-quality-inspection! store (:press-line-id request)
                                       (:run-id proposal) (:quality-grade proposal))))

(defn build
  "Compiles an OperationActor graph bound to `store`. opts:
    :advisor      -- a `printing.advisor/Advisor` (default: mock-advisor)
    :checkpointer -- a `langgraph.checkpoint/Checkpointer`
                     (default: in-memory `cp/mem-checkpointer`)"
  [store & [{:keys [advisor checkpointer]
             :or   {advisor      (advisor/mock-advisor)
                    checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels
        {:request     {:default nil}
         :context     {:default nil}
         :proposal    {:default nil}
         :verdict     {:default nil}
         :disposition {:default nil}
         :record      {:default nil}
         :approval    {:default nil}
         :audit       {:reducer into :default []}}})

      (g/add-node :intake (fn [s] s))

      (g/add-node :advise
        (fn [{:keys [request]}]
          (let [p (advisor/-advise advisor store request)]
            {:proposal p :audit [(advisor/trace request p)]})))

      (g/add-node :govern
        (fn [{:keys [request context proposal]}]
          {:verdict (governor/check request context proposal store)}))

      (g/add-node :decide
        (fn [{:keys [request context proposal verdict]}]
          (let [base (phase/verdict->disposition verdict)
                ph   (:phase context phase/default-phase)
                {:keys [disposition reason]} (phase/gate ph request base)]
            (case disposition
              :hold
              {:disposition :hold
               :audit [(cond-> (governor/hold-fact request context verdict)
                         reason (assoc :phase-reason reason :phase ph))]}

              :escalate
              {:disposition :escalate
               :audit [{:t :approval-requested
                        :op (:op request) :subject (:press-line-id request)
                        :reason (or reason
                                    (cond (:high-stakes? verdict) :always-escalate
                                          :else :low-confidence))
                        :phase ph
                        :confidence (:confidence verdict)}]}

              :commit
              {:disposition :commit
               :record (commit-record request context proposal)}))))

      (g/add-node :request-approval
        (fn [{:keys [request context proposal approval verdict]}]
          (if (= :approved (:status approval))
            {:disposition :commit
             :record (assoc (commit-record request context proposal)
                            :payload (assoc (:value proposal)
                                            :approved-by (:by approval)))
             :audit [{:t :approval-granted :op (:op request)
                      :subject (:press-line-id request) :by (:by approval)}]}
            {:disposition :hold
             :audit [(merge (governor/hold-fact request context
                                                (assoc verdict :violations
                                                       [{:rule :approver-rejected}]))
                            {:t :approval-rejected})]})))

      (g/add-node :commit
        (fn [{:keys [request context proposal]}]
          (let [f (commit-fact request context proposal)]
            (store/append-ledger! store f)
            (record-quality-inspection-if-applicable! store request proposal)
            {:audit [f]})))

      (g/add-node :hold
        (fn [{:keys [audit]}]
          (when-let [hf (last (filter #(#{:governor-hold :approval-rejected} (:t %)) audit))]
            (store/append-ledger! store (assoc hf :disposition :hold)))
          {}))

      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)

      (g/add-conditional-edges :decide
        (fn [{:keys [disposition]}]
          (case disposition
            :commit   :commit
            :escalate :request-approval
            :hold)))

      (g/add-conditional-edges :request-approval
        (fn [{:keys [disposition]}]
          (if (= :commit disposition) :commit :hold)))

      (g/set-finish-point :commit)
      (g/set-finish-point :hold)

      (g/compile-graph
       {:checkpointer     checkpointer
        :interrupt-before #{:request-approval}})))
