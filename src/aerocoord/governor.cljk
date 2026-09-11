(ns aerocoord.governor
  "AeroCoordGovernor — the independent safety/traceability layer named
  in this repository's README/business-model.md, gating every
  maintenance-bay scheduling/logistics coordination proposal an
  advisor may make for an aircraft engine under maintenance. The
  governor never dispatches hardware itself, never performs aircraft-
  engine maintenance work, and never allows a proposal to finalize a
  maintenance-execution decision (performing/completing the actual
  repair), finalize an airworthiness-clearance/return-to-service
  determination, or override a certified aviation inspector's/
  mechanic's judgment — this actor coordinates MAINTENANCE-BAY
  SCHEDULING/LOGISTICS ONLY. Modeled on cloud-itonami-isco-7413's
  linecoord.governor.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. aircraft provenance    — the aircraft/engine record must be
                                 independently verified/registered
                                 before any action.
    2. no-actuation            — proposal :effect must be :propose
                                 (the governor never dispatches
                                 hardware and never performs aircraft-
                                 engine maintenance work; it only
                                 gates what the advisor may
                                 coordinate).
    3. closed op-allowlist     — :op must be one of the four
                                 coordination ops (:log-work-record,
                                 :schedule-crew-operation,
                                 :flag-safety-concern,
                                 :coordinate-supply-order). No op that
                                 directly finalizes a maintenance-
                                 execution decision, finalizes an
                                 airworthiness-clearance/return-to-
                                 service determination, or overrides
                                 certified aviation-inspector/mechanic
                                 authority exists in this allowlist —
                                 these decision classes are
                                 structurally absent, not merely
                                 gated.
    4. aircraft-mismatch       — if the proposal names an aircraft, it
                                 must be the SAME aircraft verified for
                                 this request (defense-in-depth
                                 against a proposal quietly targeting
                                 a different, unverified aircraft).
    5. mechanic basis          — if the proposal references a
                                 mechanic, that mechanic must be a
                                 REGISTERED certified mechanic
                                 belonging to this aircraft (an
                                 unregistered or foreign-aircraft
                                 mechanic reference is not a routine
                                 scheduling proposal).
    6. scope-exclusion         — a proposal that attempts to finalize
                                 a maintenance-execution decision (to
                                 perform or complete the actual
                                 repair), to finalize an airworthiness-
                                 clearance or return-to-service
                                 determination, or to override a
                                 certified aviation inspector's or
                                 mechanic's judgment, is a hard,
                                 PERMANENT block — never overridable by
                                 human approval, regardless of
                                 confidence or stake, and NEVER auto-
                                 commit-eligible under any confidence
                                 level. Detected as finalization/
                                 execution ACTION PHRASES (e.g.
                                 'declare the engine airworthy and
                                 cleared for return to service',
                                 'finalize the repair', 'override the
                                 certified aviation inspector's
                                 judgment') in free-text proposal
                                 fields, never as bare domain nouns
                                 ('engine', 'airworthiness', 'repair',
                                 'inspection', 'return to service') —
                                 bare-noun matching would false-trip on
                                 the default mock advisor's own
                                 routine rationale text, since this
                                 actor's entire domain is aircraft-
                                 engine maintenance. See
                                 `aerocoord.governor-test`
                                 `default-mock-advisor-proposals-never-self-trip-scope-exclusion`.

  ESCALATION invariants (:escalate? true, ALWAYS human sign-off,
  regardless of confidence):
    7. :op :flag-safety-concern always escalates (a surfaced engine-
                                 defect/anomaly/safety concern always
                                 requires human review — the governor
                                 never resolves a safety concern
                                 itself, and this is unconditional —
                                 no confidence-level exception).
    8. :op :coordinate-supply-order with :cost above
                                 `supply-order-cost-threshold` always
                                 escalates.
    9. low confidence (< `confidence-floor`)."
  (:require [kotoba.lang.text :as str]
            [aerocoord.store :as store]))

(def confidence-floor 0.6)

(def supply-order-cost-threshold 20000)

(def ^:private allowed-ops
  #{:log-work-record :schedule-crew-operation :flag-safety-concern
    :coordinate-supply-order})

(def ^:private always-escalate-ops #{:flag-safety-concern})

;; Scope-exclusion is matched as finalization/execution ACTION
;; PHRASES, never as bare nouns ("engine", "airworthiness", "repair",
;; "inspection", "return to service") — this actor's entire domain is
;; aircraft-engine maintenance, so bare-noun matching would false-trip
;; on the default mock advisor's own routine rationale text (e.g.
;; "proposed :coordinate-supply-order for aircraft AC-1" naming engine
;; parts, or a crew-schedule proposal naming an engine under repair).
;; See governor-test's dedicated self-trip guard.
(def ^:private scope-exclusion-phrases
  ["declare the engine airworthy and cleared for return to service"
   "declare the aircraft airworthy and cleared for return to service"
   "clear the engine for return to service"
   "clear the aircraft for return to service"
   "finalize the airworthiness clearance"
   "finalize the airworthiness determination"
   "finalize the return-to-service determination"
   "finalize the return to service determination"
   "finalize the repair"
   "finalize the maintenance-execution decision"
   "finalize the maintenance execution decision"
   "complete the repair directly"
   "perform the repair directly"
   "perform the engine repair directly"
   "execute the repair directly"
   "execute the maintenance work directly"
   "dispatch the mechanic to perform the repair"
   "sign off the airworthiness release"
   "sign the airworthiness release"
   "issue the airworthiness release"
   "override the certified aviation inspector's judgment"
   "override the certified aviation inspector"
   "override the certified mechanic's judgment"
   "override the certified aviation mechanic's judgment"
   "bypass the certified aviation inspector"
   "bypass the airworthiness inspection"
   "bypass the return-to-service determination"])

(defn- scope-excluded-text [proposal]
  (str/lower (str (:rationale proposal) " " (:description proposal))))

(defn scope-exclusion-violation?
  "true if any free-text field of `proposal` contains a
  finalization/execution action phrase attempting to finalize a
  maintenance-execution decision, finalize an airworthiness-clearance/
  return-to-service determination, or override certified aviation-
  inspector/mechanic authority. Phrased as multi-word action phrases
  (never bare nouns) so this never false-trips on legitimate aircraft-
  engine-maintenance domain vocabulary."
  [proposal]
  (let [text (scope-excluded-text proposal)]
    (boolean (some #(str/includes? text %) scope-exclusion-phrases))))

(defn- hard-violations [{:keys [request proposal]} aircraft-record m]
  (let [{:keys [op aircraft-id mechanic-id]} proposal]
    (cond-> []
      (nil? aircraft-record)
      (conj {:rule :no-aircraft :detail "未登録 aircraft/engine record"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（governor は整備作業判断を直接実行しない）"})

      (not (contains? allowed-ops op))
      (conj {:rule :unknown-op :detail "closed op-allowlist 外の op（整備作業実行の完了・耐空性証明/復帰判定の確定・認定検査官/整備士の判断の上書きにあたる op は許可されていない）"})

      (and aircraft-id (not= aircraft-id (:aircraft-id request)))
      (conj {:rule :aircraft-mismatch :detail "proposal の aircraft が request で検証済みの aircraft と一致しない"})

      (and mechanic-id (nil? m))
      (conj {:rule :unknown-mechanic :detail "未登録 mechanic への提案は不可"})

      (and m (not= (:aircraft-id m) (:aircraft-id request)))
      (conj {:rule :mechanic-wrong-aircraft :detail "mechanic が別 aircraft 所属"})

      (scope-exclusion-violation? proposal)
      (conj {:rule :scope-exclusion-violation
             :detail "整備作業実行の完了・耐空性証明/運航復帰判定の確定・認定航空検査官/整備士の判断の上書きにあたる提案は恒久的に禁止（human 承認でも上書き不可）"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `aerocoord.store/Store`. Pure — never mutates
  the store, never dispatches a robot action, never performs aircraft-
  engine maintenance work."
  [request context proposal store]
  (let [aircraft-record (store/aircraft store (:aircraft-id request))
        m (some->> (:mechanic-id proposal) (store/mechanic store))
        hard (hard-violations {:request request :proposal proposal} aircraft-record m)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        cost (:cost proposal)
        over-threshold? (and (= :coordinate-supply-order (:op proposal))
                              (number? cost) (> cost supply-order-cost-threshold))
        always-risky? (or (contains? always-escalate-ops (:op proposal)) over-threshold?)]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
