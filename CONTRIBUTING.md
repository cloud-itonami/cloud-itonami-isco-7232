# Contributing

`cloud-itonami-isco-7232` accepts contributions to the OSS actor, policy tests,
documentation, examples and open occupation blueprint.

## Development

```bash
clojure -M:test
```

Keep changes small and include tests for policy, audit, store or scope-exclusion
behavior.

## Rules

- Do not commit real client, crew or operating documents.
- Keep production writes behind AeroCoordGovernor.
- Never add an op to the allowlist that could finalize a
  maintenance-execution decision, finalize an airworthiness-clearance
  or return-to-service determination, or override a certified aviation
  inspector's or mechanic's judgment — such proposals must remain a
  hard, permanent block, never an always-escalate op, never
  auto-commit-eligible under any confidence level.
- Treat this occupation's workflows as high-risk: add tests for permission,
  scope-exclusion, safety and audit logging.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests

PRs should describe:

- what behavior changed
- which policy invariant is affected
- how it was tested
- whether operator or certification docs need updates
