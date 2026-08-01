#!/usr/bin/env bash
# Local SonarQube analysis for provisioning-rabbitmq.
# Spins up an ephemeral SonarQube Community container, runs the Maven build with
# coverage, scans the project against the local instance, and prints the quality
# gate, the measures, and the open issues.
#
# Usage: ./scripts/sonar-local.sh
#
# Requirements:
#   - Docker running (SonarQube, the sonar-scanner image, and Testcontainers)
#   - Maven and a JDK 21+ on PATH
#   - curl and python3 on PATH (used to drive the SonarQube REST API and parse JSON)
#   - The sonar.* properties in pom.xml are honoured as-is
#
# IMPORTANT — limitation of an ephemeral SonarQube:
# The container starts fresh on every run, so it has no previous analysis to use as
# a "new code" baseline. The quality gate's new-code conditions therefore evaluate
# trivially OK and do NOT match SonarCloud, where a real baseline exists. To
# approximate PR-style focus, this script intersects the issue list with
# `git diff --name-only <base>...HEAD` and reports only issues on changed files.
# Treat it as a smoke check; SonarCloud on the actual pull request is authoritative.
set -euo pipefail

CONTAINER_NAME="provisioning-rabbitmq-sonarqube"
SONAR_PORT="${SONAR_PORT:-9000}"
PROJECT_KEY="provisioning-rabbitmq"
SONAR_URL="http://localhost:${SONAR_PORT}"

cleanup() {
    echo "Stopping SonarQube..."
    docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

if ! docker info >/dev/null 2>&1; then
    echo "ERROR: Docker is required (used for SonarQube, the scanner image and Testcontainers)." >&2
    exit 1
fi

for tool in mvn curl python3; do
    if ! command -v "${tool}" >/dev/null 2>&1; then
        echo "ERROR: ${tool} not found on PATH." >&2
        exit 1
    fi
done

docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true

echo "Starting SonarQube Community on port ${SONAR_PORT}..."
docker run -d --name "${CONTAINER_NAME}" -p "${SONAR_PORT}:9000" sonarqube:community >/dev/null

echo "Waiting for SonarQube to be ready (up to 2 minutes)..."
for i in $(seq 1 120); do
    if curl -sf "${SONAR_URL}/api/system/status" 2>/dev/null | grep -q '"status":"UP"'; then
        echo "SonarQube is ready."
        break
    fi
    if [[ "$i" -eq 120 ]]; then
        echo "ERROR: SonarQube failed to start within 2 minutes." >&2
        exit 1
    fi
    sleep 1
done

echo "Configuring SonarQube..."
# SonarQube ships with `admin/admin` as the factory default and forces a change on
# first login. Rotating it to a policy-compliant value lets the rest of the script
# authenticate. Both values are scoped to this ephemeral container, which the EXIT
# trap removes, so neither is persisted anywhere.
curl -s -o /dev/null -u admin:admin -X POST \
    "${SONAR_URL}/api/users/change_password?login=admin&previousPassword=admin&password=Admin12345678!" 2>/dev/null || true

if curl -sf -u admin:Admin12345678! "${SONAR_URL}/api/system/status" >/dev/null 2>&1; then
    SONAR_CREDS="admin:Admin12345678!"
elif curl -sf -u admin:admin "${SONAR_URL}/api/system/status" >/dev/null 2>&1; then
    SONAR_CREDS="admin:admin"
else
    echo "ERROR: Cannot authenticate to SonarQube." >&2
    exit 1
fi

TOKEN_NAME="local-$(date +%s)"
TOKEN=$(curl -sf -u "${SONAR_CREDS}" -X POST \
    "${SONAR_URL}/api/user_tokens/generate?name=${TOKEN_NAME}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

if [[ -z "${TOKEN}" ]]; then
    echo "ERROR: Failed to generate SonarQube token." >&2
    exit 1
fi

echo "Running the Maven build with coverage..."
mvn -B clean verify

if [[ ! -f target/site/jacoco/jacoco.xml ]]; then
    echo "ERROR: JaCoCo produced no report at target/site/jacoco/jacoco.xml." >&2
    exit 1
fi

echo "Running the scanner against ${SONAR_URL}..."
SONAR_LOG=$(mktemp)
mvn -B org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
    -Dsonar.host.url="${SONAR_URL}" \
    -Dsonar.token="${TOKEN}" \
    -Dsonar.projectKey="${PROJECT_KEY}" \
    -Dsonar.organization= \
    | tee "${SONAR_LOG}"

# The scanner logs "More about the report processing at <url>/api/ce/task?id=<uuid>".
# The server only publishes measures and the quality gate once that task finishes.
TASK_ID=$(grep -oE "api/ce/task\?id=[A-Za-z0-9_-]+" "${SONAR_LOG}" | head -1 | sed 's/.*id=//')
rm -f "${SONAR_LOG}"

echo ""
echo "=== SonarQube Results ==="
echo "Dashboard: ${SONAR_URL}/dashboard?id=${PROJECT_KEY}"

if [[ -n "${TASK_ID:-}" ]]; then
    echo ""
    echo "Waiting for analysis report processing (task ${TASK_ID}, up to 2 minutes)..."
    for i in $(seq 1 60); do
        TASK_STATUS=$(curl -s -u "${SONAR_CREDS}" \
            "${SONAR_URL}/api/ce/task?id=${TASK_ID}" 2>/dev/null \
            | python3 -c "import sys,json; print(json.load(sys.stdin).get('task', {}).get('status', ''))" 2>/dev/null || true)
        if [[ "${TASK_STATUS}" == "SUCCESS" ]]; then
            echo "Analysis report processed."
            break
        fi
        if [[ "${TASK_STATUS}" == "FAILED" || "${TASK_STATUS}" == "CANCELED" ]]; then
            echo "ERROR: Sonar analysis task ${TASK_STATUS}." >&2
            exit 1
        fi
        sleep 2
    done
fi

BASE_BRANCH="${BASE_BRANCH:-main}"
CHANGED=$(git diff --name-only "${BASE_BRANCH}...HEAD" 2>/dev/null || true)
if [[ -z "${CHANGED}" ]]; then
    echo ""
    echo "(No changes vs ${BASE_BRANCH}; reporting the whole project.)"
    SCOPE_DESC="all project files"
else
    echo ""
    echo "Files changed vs ${BASE_BRANCH}:"
    while IFS= read -r changed_file; do
        echo "  ${changed_file}"
    done <<< "${CHANGED}"
    SCOPE_DESC="changed files only"
fi

echo ""
echo "================================================================================"
echo "EPHEMERAL SONARQUBE — LIMITATION"
echo "================================================================================"
echo "  This run uses a freshly-started SonarQube container with no previous"
echo "  analysis to act as a 'new code' baseline. The quality gate evaluates"
echo "  trivially OK and does NOT match SonarCloud, where a real baseline exists."
echo "  Treat this as a smoke check; SonarCloud on the pull request is authoritative."
echo "================================================================================"

echo ""
echo "Quality gate (whole project; new-code conditions trivially OK without a baseline):"
curl -s -u "${SONAR_CREDS}" \
    "${SONAR_URL}/api/qualitygates/project_status?projectKey=${PROJECT_KEY}" 2>/dev/null \
    | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)['projectStatus']
    print(f'  Status: {d[\"status\"]}')
    for c in d.get('conditions', []):
        print(f'  {c[\"metricKey\"]}: {c[\"actualValue\"]} (threshold: {c[\"errorThreshold\"]}) - {c[\"status\"]}')
except Exception as e:
    print(f'  WARN: Quality gate not yet available ({e}). See the dashboard.')
"

echo ""
echo "Coverage and duplication summary:"
curl -s -u "${SONAR_CREDS}" \
    "${SONAR_URL}/api/measures/component?component=${PROJECT_KEY}&metricKeys=coverage,line_coverage,branch_coverage,duplicated_lines_density,ncloc,bugs,vulnerabilities,code_smells,security_hotspots" \
    | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    measures = {m['metric']: m['value'] for m in d.get('component', {}).get('measures', [])}
    order = ['coverage', 'line_coverage', 'branch_coverage', 'duplicated_lines_density', 'ncloc', 'bugs', 'vulnerabilities', 'code_smells', 'security_hotspots']
    for k in order:
        if k in measures:
            print(f'  {k}: {measures[k]}')
except Exception as e:
    print(f'  WARN: Measures not available ({e}).')
"

echo ""
echo "Issues — ${SCOPE_DESC} (filtered to the diff vs ${BASE_BRANCH}; not a full project view):"
curl -s -u "${SONAR_CREDS}" \
    "${SONAR_URL}/api/issues/search?projectKeys=${PROJECT_KEY}&issueStatuses=OPEN,CONFIRMED&ps=500" \
    | CHANGED_LIST="${CHANGED}" python3 -c "
import sys, json, os
changed = [f.strip() for f in os.environ.get('CHANGED_LIST', '').splitlines() if f.strip()]
try:
    d = json.load(sys.stdin)
    issues = d.get('issues', [])
    if changed:
        issues = [i for i in issues if any(i.get('component', '').endswith(f) for f in changed)]
    print(f'  Total in scope: {len(issues)}')
    for i in issues[:50]:
        comp = i['component'].split(':')[-1]
        line = i.get('line', '?')
        print(f'  [{i[\"severity\"]}] {comp}:{line} - {i[\"message\"]} ({i[\"rule\"]})')
except Exception as e:
    print(f'  WARN: Issues list not available ({e}).')
"

echo ""
echo "Done. (Set BASE_BRANCH=<other> to compare against a different base; default: main.)"
