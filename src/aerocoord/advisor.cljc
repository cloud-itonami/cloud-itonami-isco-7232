(ns aerocoord.advisor
  "Aircraft Engine Maintenance-Bay Coordination Advisor — the advisor
  named in this repository's README, proposing a maintenance-bay
  scheduling/logistics coordination operation (log a work record,
  schedule a crew/bay operation, flag a safety concern, coordinate a
  supply order) from an aircraft roster, mechanic roster and bay
  schedule. Swappable mock/llm; the advisor ONLY proposes —
  `aerocoord.governor` checks aircraft/mechanic registration, the
  closed op-allowlist and scope-exclusion independently, and always
  escalates safety-concern flags, above-threshold supply orders and
  low-confidence proposals. This actor coordinates MAINTENANCE-BAY
  SCHEDULING/LOGISTICS ONLY — it never performs aircraft-engine
  maintenance work and never proposes to finalize a maintenance-
  execution decision, an airworthiness-clearance/return-to-service
  determination, or override a certified aviation inspector's/
  mechanic's judgment. Modeled on cloud-itonami-isco-7413's
  linecoord.advisor.

  A proposal: {:op :log-work-record|:schedule-crew-operation|
               :flag-safety-concern|:coordinate-supply-order
               :effect :propose :aircraft-id str :mechanic-id str? :cost
               number? :stake kw :confidence n :rationale str}")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake aircraft-id mechanic-id cost task materials
                             concern-type severity description time-window
                             progress-notes defect-report]
                      :as request}]
  (cond-> {:op op
           :effect :propose
           :aircraft-id aircraft-id
           :stake (or stake :low)
           :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
           :rationale (str "proposed " (name op) " for aircraft " aircraft-id)}
    mechanic-id (assoc :mechanic-id mechanic-id)
    (some? cost) (assoc :cost cost)
    task (assoc :task task)
    materials (assoc :materials materials)
    concern-type (assoc :concern-type concern-type)
    severity (assoc :severity severity)
    description (assoc :description description)
    time-window (assoc :time-window time-window)
    progress-notes (assoc :progress-notes progress-notes)
    defect-report (assoc :defect-report defect-report)))

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are an aircraft-engine-mechanics maintenance-bay scheduling/
   logistics coordination advisor. Given a request, propose an :op
   (:log-work-record, :schedule-crew-operation, :flag-safety-concern
   or :coordinate-supply-order ONLY — no other op exists), the
   :aircraft-id, an honest :confidence and a :stake. You coordinate
   maintenance-bay scheduling and logistics ONLY: never propose to
   finalize a maintenance-execution decision, never propose to
   finalize an airworthiness-clearance or return-to-service
   determination, never propose to override a certified aviation
   inspector's or mechanic's judgment, and never propose an op outside
   the closed allowlist above. The governor checks aircraft/mechanic
   registration and scope independently. Safety-concern flags and
   above-threshold supply orders always require human sign-off
   regardless of confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
