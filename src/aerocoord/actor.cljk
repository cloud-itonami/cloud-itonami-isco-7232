(ns aerocoord.actor
  "AeroCoordActor — the ISCO-08 7232 aircraft engine mechanics and
  repairers maintenance-bay scheduling/logistics coordination actor as
  a `langgraph.graph/state-graph` (ADR-2607121000 / CLAUDE.md Actors
  section). One graph run = one coordination operation request
  (intake → advise → govern → decide → commit/hold, with a
  human-approval interrupt for escalated proposals). No infinite
  internal loop; checkpointed per superstep so an interrupted run can
  resume after human sign-off. This actor coordinates MAINTENANCE-BAY
  SCHEDULING/LOGISTICS ONLY — it never performs aircraft-engine
  maintenance work itself and never commits a proposal the
  AeroCoordGovernor refuses. Modeled on cloud-itonami-isco-7413's
  linecoord.actor.

  ```text
  :intake -> :advise -> :govern -> :decide -+-> :commit            (:ok? true)
                                             +-> :request-approval   (:escalate? true, interrupt-before)
                                             +-> :hold               (:hard? true)
  ```

  The unconditional invariant: the Aircraft Engine Maintenance-Bay
  Coordination Advisor can never directly commit a coordination record
  the AeroCoordGovernor refuses — every commit-record! call is gated
  behind `:decide`, and the governor's scope-exclusion rule makes any
  proposal to finalize a maintenance-execution decision, finalize an
  airworthiness-clearance/return-to-service determination, or override
  a certified aviation inspector's/mechanic's judgment an
  unconditional, permanent :hold."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [aerocoord.advisor :as advisor]
            [aerocoord.governor :as governor]
            [aerocoord.store :as store]))

(defn build-graph
  "Build a compiled AeroCoordActor graph. `store` implements
  `aerocoord.store/Store`. `advisor` implements
  `aerocoord.advisor/Advisor` (defaults to `mock-advisor`).
  `checkpointer` defaults to an in-memory one."
  [{:keys [store advisor checkpointer]
    :or {advisor (advisor/mock-advisor)
         checkpointer (cp/mem-checkpointer)}}]
  (-> (g/state-graph
       {:channels
        {:request     {:default nil}
         :context     {:default nil}
         :proposal    {:default nil}
         :verdict     {:default nil}
         :disposition {:default nil}
         :record      {:default nil}
         :audit       {:reducer into :default []}}})
      (g/add-node :intake (fn [s] s))
      (g/add-node :advise
                   (fn [{:keys [request]}]
                     (let [p (advisor/-advise advisor store request)]
                       {:proposal p
                        :audit [{:node :advise :request request :proposal p}]})))
      (g/add-node :govern
                   (fn [{:keys [request context proposal]}]
                     (let [v (governor/check request context proposal store)]
                       {:verdict v
                        :audit [{:node :govern :verdict v}]})))
      (g/add-node :decide
                   (fn [{:keys [verdict]}]
                     {:disposition (cond
                                     (:hard? verdict) :hold
                                     (:escalate? verdict) :request-approval
                                     :else :commit)}))
      (g/add-node :request-approval (fn [s] s))
      (g/add-node :commit
                   (fn [{:keys [request proposal]}]
                     (let [record {:aircraft-id (:aircraft-id request)
                                    :op (:op proposal)
                                    :mechanic-id (:mechanic-id proposal)
                                    :payload proposal}]
                       (store/commit-record! store record)
                       (store/append-ledger! store {:disposition :commit :record record})
                       {:record record
                        :audit [{:node :commit :record record}]})))
      (g/add-node :hold
                   (fn [{:keys [verdict]}]
                     (store/append-ledger! store {:disposition :hold :verdict verdict})
                     {:audit [{:node :hold :verdict verdict}]}))
      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)
      (g/add-conditional-edges
       :decide
       (fn [{:keys [disposition]}]
         (case disposition
           :commit :commit
           :request-approval :request-approval
           :hold)))
      (g/add-edge :request-approval :commit)
      (g/set-finish-point :commit)
      (g/set-finish-point :hold)
      (g/compile-graph {:checkpointer checkpointer
                         :interrupt-before #{:request-approval}})))

(defn run-request!
  "Run one operation request to completion or interrupt. `thread-id`
  scopes checkpointing for resume after human approval."
  [graph request context thread-id]
  (g/run* graph {:request request :context context} {:thread-id thread-id}))

(defn approve!
  "Human-in-the-loop resume: the interrupted `:request-approval` node
  advances straight to `:commit` on resume (approval is the act of
  resuming the thread). NOTE: `:request-approval` is only reachable
  when `:hard?` is false — the governor's scope-exclusion rule and
  other hard invariants route to `:hold` instead, so this resume path
  can never be used to approve a maintenance-execution-decision
  finalization, an airworthiness-clearance/return-to-service
  determination, or a certified-aviation-inspector's-/mechanic's-
  judgment override."
  [graph thread-id]
  (g/run* graph nil {:thread-id thread-id :resume? true}))
