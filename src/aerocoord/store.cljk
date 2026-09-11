(ns aerocoord.store
  "SSoT for the ISCO-08 7232 aircraft engine mechanics and repairers
  maintenance-bay scheduling/logistics coordination actor (itonami
  actor pattern, ADR-2607121000 / CLAUDE.md Actors section; README's
  'Robotics premise' — a maintenance-bay scheduling/logistics
  coordination robot proposes crew/bay scheduling, work-record
  logging, safety-concern flags and parts/consumables order
  coordination under this advisor/governor pair, which never
  dispatches hardware itself, never performs aircraft-engine
  maintenance work, and never finalizes a maintenance-execution
  decision, an airworthiness-clearance/return-to-service
  determination, or overrides a certified aviation inspector's/
  mechanic's judgment). Modeled on cloud-itonami-isco-7413's
  linecoord.store.

  Domain:

    aircraft — a registered aircraft/engine unit under maintenance
               (:aircraft-id, :name, :bay).
    mechanic — a registered certified aircraft engine mechanic
               {:mechanic-id :aircraft-id :name :role}, belonging to
               exactly one registered aircraft (the aircraft/bay
               currently assigned to this mechanic for this
               maintenance engagement).
    record   — a committed operating record (a logged work record,
               scheduling proposal, safety-concern flag or supply-
               order coordination entry) — written ONLY via
               commit-record!. This actor coordinates maintenance-bay
               scheduling/logistics ONLY — a `record` is a
               coordination artifact, never a maintenance-execution
               act, an airworthiness-clearance/return-to-service
               determination, or a certified-aviation-inspector's-/
               mechanic's-judgment override.
    ledger   — append-only audit trail, commit or hold.")

(defprotocol Store
  (aircraft [s aircraft-id])
  (mechanic [s mechanic-id])
  (records-of [s aircraft-id])
  (ledger [s])
  (register-aircraft! [s ac])
  (register-mechanic! [s m])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (aircraft [_ aircraft-id] (get-in @a [:aircraft aircraft-id]))
  (mechanic [_ mechanic-id] (get-in @a [:mechanics mechanic-id]))
  (records-of [_ aircraft-id] (filter #(= aircraft-id (:aircraft-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-aircraft! [s ac]
    (swap! a assoc-in [:aircraft (:aircraft-id ac)] ac) s)
  (register-mechanic! [s m]
    (swap! a assoc-in [:mechanics (:mechanic-id m)] m) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:aircraft {} :mechanics {} :records [] :ledger []}
                                   seed)))))
