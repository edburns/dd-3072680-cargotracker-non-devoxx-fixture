#!/usr/bin/env bash
set -euo pipefail

REPO='edburns/dd-3072680-cargotracker-non-devoxx-fixture'
PARENT=1
LOG='/home/edburns/workareas/dd-3072680-cargotracker-non-devoxx-fixture-shepherd-control/1-arrival-deadline-control-remove-before-merge/prompts/shepherd-task-20-20261002-1105'
BODY_DIR="$LOG/issue-bodies"
LEDGER="$LOG/creation-ledger.json"
RESULT="$LOG/stage-20-result.json"
BODY_VERIFIER='/home/edburns/.copilot/plugins/shepherd-task/scripts/verify-github-issue-body.sh'
CHILD_VERIFIER='/home/edburns/.copilot/plugins/shepherd-task/scripts/verify-stage20-child-links.sh'

atomic_write() {
  local path="$1" content="$2" temp
  temp="$(mktemp "${path}.tmp.XXXXXX")"
  printf '%s\n' "$content" >"$temp"
  mv "$temp" "$path"
}

normalize_children() {
  jq 'if length == 0 then [] elif all(.[]; type == "array") then add else . end'
}

update_ledger_flag() {
  local number="$1" field="$2" value="$3" updated
  updated="$(
    jq \
      --argjson number "$number" \
      --arg field "$field" \
      --argjson value "$value" \
      'map(if .number == $number then .[$field] = $value else . end)' \
      "$LEDGER"
  )" || return 1
  atomic_write "$LEDGER" "$updated"
}

fail_stage() {
  local operation="$1" error="$2" raw children reconciled failed
  set +e
  raw="$(gh api "repos/$REPO/issues/$PARENT/sub_issues" --paginate --slurp 2>&1)"
  if [[ $? -eq 0 ]]; then
    children="$(printf '%s' "$raw" | normalize_children 2>/dev/null)"
    if [[ $? -eq 0 ]]; then
      reconciled="$(
        jq \
          --argjson children "$children" \
          'map(. as $entry | .linked = ([ $children[].id ] | index($entry.id) != null))' \
          "$LEDGER" 2>/dev/null
      )"
      [[ $? -ne 0 ]] || atomic_write "$LEDGER" "$reconciled"
    fi
  fi
  failed="$(
    jq -n \
      --arg operation "$operation" \
      --arg error "$error" \
      '{
        schemaVersion: 1,
        status: "failed",
        ledgerFile: "creation-ledger.json",
        operationError: {operation: $operation, error: $error}
      }'
  )"
  atomic_write "$RESULT" "$failed"
  printf 'FAILED OPERATION: %s\nERROR: %s\n' "$operation" "$error" >&2
  if [[ "$(jq 'length' "$LEDGER" 2>/dev/null)" == 0 ]]; then
    printf 'No issues were created; no cleanup is required.\n' >&2
  else
    jq -r '.[] | "issue #\(.number) | \(.title) | \(.url) | \(.bodyFile) | body_verified=\(.body_verified) | linked=\(.linked)"' "$LEDGER" >&2
    jq -r --arg repo "$REPO" '.[] | "gh issue delete \(.number) --repo \"\($repo)\" --yes"' "$LEDGER" >&2
    printf 'The operation did not complete and no automatic rollback was performed. Delete every issue in the ledger before invoking this skill again.\n' >&2
  fi
  exit 1
}

titles=(
  '4.1 — Add the application-layer deadline change operation'
  '4.2 — Expose deadline changes through the booking facade'
  '4.3 — Implement the deadline editor backing model'
  '4.4 — Implement the PrimeFaces deadline dialog'
  '4.5 — Integrate deadline editing into the Administration dashboard'
)
subsections=('4.1' '4.2' '4.3' '4.4' '4.5')
bodies=(
  "$BODY_DIR/01-4.1-body.md"
  "$BODY_DIR/02-4.2-body.md"
  "$BODY_DIR/03-4.3-body.md"
  "$BODY_DIR/04-4.4-body.md"
  "$BODY_DIR/05-4.5-body.md"
)

atomic_write "$LEDGER" '[]'
atomic_write "$RESULT" '{
  "schemaVersion": 1,
  "status": "in_progress",
  "ledgerFile": "creation-ledger.json",
  "operationError": null
}'

raw_children="$(gh api "repos/$REPO/issues/$PARENT/sub_issues" --paginate --slurp 2>&1)" ||
  fail_stage 'pre-creation child query' "$raw_children"
baseline="$(printf '%s' "$raw_children" | normalize_children 2>&1)" ||
  fail_stage 'pre-creation child normalization' "$baseline"
atomic_write "$LOG/pre-creation-children.json" "$baseline"

for index in "${!titles[@]}"; do
  title="${titles[$index]}"
  subsection="${subsections[$index]}"
  body="${bodies[$index]}"
  response="$(gh api "repos/$REPO/issues" -X POST -f "title=$title" -F "body=@$body" 2>&1)" ||
    fail_stage "create issue for subsection $subsection" "$response"

  issue_id="$(jq -er '.id | numbers' <<<"$response" 2>&1)" ||
    fail_stage "parse created issue for subsection $subsection" "$issue_id"
  issue_number="$(jq -er '.number | numbers' <<<"$response" 2>&1)" ||
    fail_stage "parse created issue number for subsection $subsection" "$issue_number"
  issue_title="$(jq -er '.title | strings' <<<"$response" 2>&1)" ||
    fail_stage "parse created issue title for subsection $subsection" "$issue_title"
  issue_url="$(jq -er '.html_url | strings' <<<"$response" 2>&1)" ||
    fail_stage "parse created issue URL for subsection $subsection" "$issue_url"
  relative_body="${body#"$LOG/"}"

  appended="$(
    jq \
      --arg implementationSubsection "$subsection" \
      --arg bodyFile "$relative_body" \
      --argjson id "$issue_id" \
      --argjson number "$issue_number" \
      --arg title "$issue_title" \
      --arg url "$issue_url" \
      '. + [{
        implementationSubsection: $implementationSubsection,
        bodyFile: $bodyFile,
        id: $id,
        number: $number,
        title: $title,
        url: $url,
        body_verified: false,
        linked: false
      }]' \
      "$LEDGER"
  )" || fail_stage "append issue #$issue_number to ledger" 'jq failed'
  atomic_write "$LEDGER" "$appended"

  verified="$(
    "$BODY_VERIFIER" \
      "$REPO" \
      "$issue_number" \
      "$body" \
      6 \
      5 \
      "$LOG/issue-$issue_number-body-verification-failure.json" 2>&1
  )" || fail_stage "verify body for issue #$issue_number" "$verified"
  update_ledger_flag "$issue_number" body_verified true ||
    fail_stage "update body verification ledger for issue #$issue_number" 'ledger update failed'

  linked=false
  link_error=''
  for attempt in 1 2 3; do
    if link_output="$(
        printf '{"sub_issue_id": %s}' "$issue_id" |
          gh api "repos/$REPO/issues/$PARENT/sub_issues" -X POST --input - 2>&1
      )"; then
      linked=true
      break
    fi
    link_error="$link_output"
  done
  [[ "$linked" == true ]] ||
    fail_stage "link issue #$issue_number to parent #$PARENT" "$link_error"
  update_ledger_flag "$issue_number" linked true ||
    fail_stage "update link ledger for issue #$issue_number" 'ledger update failed'
done

raw_final="$(gh api "repos/$REPO/issues/$PARENT/sub_issues" --paginate --slurp 2>&1)" ||
  fail_stage 'final child query' "$raw_final"
final="$(printf '%s' "$raw_final" | normalize_children 2>&1)" ||
  fail_stage 'final child normalization' "$final"
atomic_write "$LOG/final-children.json" "$final"

child_check="$(
  "$CHILD_VERIFIER" \
    "$LOG/pre-creation-children.json" \
    "$LOG/final-children.json" \
    "$LEDGER" 2>&1
)" || fail_stage 'authoritative child-link verification' "$child_check"

while IFS=$'\t' read -r issue_number relative_body; do
  body="$LOG/$relative_body"
  final_issue="$(
    "$BODY_VERIFIER" \
      "$REPO" \
      "$issue_number" \
      "$body" \
      6 \
      5 \
      "$LOG/issue-$issue_number-final-body-verification-failure.json" 2>&1
  )" || fail_stage "final body verification for issue #$issue_number" "$final_issue"
  postcondition="$(
    jq -er '
      select(.state == "open")
      | select((.assignees | type) == "array" and (.assignees | length) == 0)
      | .number
    ' <<<"$final_issue" 2>&1
  )" || fail_stage "open/unassigned verification for issue #$issue_number" "$postcondition"
done < <(jq -r '.[] | [.number, .bodyFile] | @tsv' "$LEDGER")

atomic_write "$RESULT" '{
  "schemaVersion": 1,
  "status": "complete",
  "ledgerFile": "creation-ledger.json",
  "operationError": null
}'

jq -n \
  --slurpfile ledger "$LEDGER" \
  --arg issueType 'none' \
  '{status: "complete", issueType: $issueType, issues: $ledger[0]}'
