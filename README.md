# cloud-itonami-isco-7232

Open Occupation Blueprint for **ISCO-08 7232**: Aircraft Engine Mechanics and Repairers.

This repository designs a forkable OSS business for an aircraft-engine-maintenance maintenance-bay scheduling/logistics coordination service: a maintenance-bay scheduling/logistics coordination robot manages work-record logging, crew/bay scheduling, safety-concern flagging and parts/consumables order coordination under a governor-gated actor, so the maintenance organization keeps its own operating records instead of renting a closed maintenance-scheduling SaaS.

**This actor coordinates MAINTENANCE-BAY SCHEDULING/LOGISTICS ONLY — it never performs aircraft-engine maintenance work itself and never makes an airworthiness-clearance decision.** Aircraft engine mechanics and repairers inspect, service and repair aircraft engines where a single undetected defect can cause loss of life in flight — categorically higher-stakes than ordinary workshop mechanical trades. The actor's closed op-allowlist contains no op that directly finalizes an airworthiness-clearance/return-to-service determination or a maintenance-execution decision (performing/completing the actual repair), nor overrides a certified aviation inspector's/mechanic's judgment. Any proposal that attempts any of these is a hard, permanent block, never overridable by human approval, and NEVER auto-commit-eligible under any confidence level.

**Maturity: `:implemented`.** `src/aerocoord/` implements the
`AeroCoordActor` as a `langgraph.graph/state-graph`
(`aerocoord.actor`) wired to an `Aircraft Engine Maintenance-Bay
Coordination Advisor` (`aerocoord.advisor`) and an independent
`AeroCoordGovernor` (`aerocoord.governor`), following the itonami
actor pattern (ADR-2607121000): `:intake -> :advise -> :govern -> :decide -+-> :commit
(:ok? true) +-> :request-approval (:escalate? true, human-in-the-loop
interrupt) +-> :hold (:hard? true)`. See `kbb -M:test` output for
the current test/assertion counts.

HARD invariants (always `:hold`, never overridable): the aircraft/
engine record must be independently verified/registered before any
action; a referenced mechanic must be a registered certified crew
member belonging to that aircraft; `:effect` must be `:propose` only
(no hardware dispatch, no aircraft-engine maintenance work performed);
the closed op-allowlist is enforced (no op in the allowlist finalizes
a maintenance-execution decision, finalizes an airworthiness-
clearance/return-to-service determination, or overrides certified
aviation-inspector/mechanic authority); and any proposal that attempts
to directly finalize a maintenance-execution decision, finalize an
airworthiness-clearance/return-to-service determination, or override a
certified aviation inspector's/mechanic's judgment is a hard,
**permanent** block — detected as finalization/execution action
phrases (never bare nouns like "engine"/"airworthiness"/"repair"/
"inspection", which are ordinary vocabulary for this domain and must
not false-trip the guard).

Always-escalate ops (human sign-off regardless of confidence, mapping
this repo's Trust Controls in
[`docs/business-model.md`](docs/business-model.md)):
`:flag-safety-concern` (every surfaced engine-defect/anomaly/safety
concern, ALWAYS, no exceptions, ever) and `:coordinate-supply-order`
above the registered cost threshold.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a maintenance-bay scheduling/logistics coordination robot performs work-record logging, crew/bay-schedule proposals, safety-concern surfacing and parts/consumables order coordination under an actor that proposes
actions and an independent **Aircraft Engine Maintenance-Bay Coordination Governor** that gates them. The governor never
dispatches hardware itself, never performs aircraft-engine maintenance work, never finalizes an airworthiness-clearance/return-to-service determination, and never overrides a certified aviation inspector's/mechanic's judgment; `:high`/`:safety-critical` actions (such as a safety-concern flag or an above-threshold supply order) require human sign-off.

## Core Contract

```text
aircraft roster + mechanic roster + bay schedule
        |
        v
Aircraft Engine Maintenance-Bay Coordination Advisor -> AeroCoordGovernor -> log record/schedule/order, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses,
finalize a maintenance-execution decision, finalize an airworthiness-
clearance/return-to-service determination, override a certified
aviation inspector's/mechanic's judgment, suppress an operating
record, or disclose sensitive data without governor approval and audit
evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `7232`). Required capabilities:

- :robotics
- :identity
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
