#!/usr/bin/env bash
# Grafana 패널을 PNG로 저장한다 (블로그용).
# 사용법: scripts/capture-grafana.sh <이름> <시작 시각> <끝 시각> [K6 testid]
#   testid를 주면 K6 패널을 그 테스트로만 거른다 (직전 테스트의 남은 값이 섞이지 않게)
#   예:   scripts/capture-grafana.sh step01-load "2026-10-21 19:50:45" "2026-10-21 19:56:15"
# 결과:   posts/images/<이름>/<패널>.png  + 대시보드 전체 dashboard.png
set -euo pipefail

NAME=$1
FROM_MS=$(python3 -c "import datetime,sys; print(int(datetime.datetime.strptime(sys.argv[1], '%Y-%m-%d %H:%M:%S').timestamp()*1000))" "$2")
TO_MS=$(python3 -c "import datetime,sys; print(int(datetime.datetime.strptime(sys.argv[1], '%Y-%m-%d %H:%M:%S').timestamp()*1000))" "$3")

ROOT=$(cd "$(dirname "$0")/.." && pwd)
# Grafana 계정은 infra/.env에서 읽는다 (없으면 로컬 기본값)
[ -f "$ROOT/infra/.env" ] && set -a && . "$ROOT/infra/.env" && set +a
AUTH="${GRAFANA_ADMIN_USER:-admin}:${GRAFANA_ADMIN_PASSWORD:-admin}"
GRAFANA=http://localhost:3000
DASH=timedeal-app
OUT="$ROOT/posts/images/$NAME"
mkdir -p "$OUT"

COMMON="from=$FROM_MS&to=$TO_MS&tz=Asia%2FSeoul&theme=light&var-testid=${4:-All}"

# 패널 ID와 파일 이름 (infra/grafana/dashboards/timedeal-app.json의 id와 맞춘다)
PANELS=(
  "2:k6-vus-rps"
  "3:k6-latency"
  "4:k6-failed"
  "6:server-rps"
  "7:server-latency"
  "9:hikari-connections"
  "10:hikari-acquire"
  "11:hikari-usage"
  "12:tomcat-threads"
  "14:cpu"
  "15:heap"
  "16:gc"
)

for entry in "${PANELS[@]}"; do
  id=${entry%%:*}
  file=${entry#*:}
  curl -sf -u "$AUTH" -o "$OUT/$file.png" \
    "$GRAFANA/render/d-solo/$DASH?panelId=$id&$COMMON&width=1000&height=420"
  echo "saved $OUT/$file.png"
done

curl -sf -u "$AUTH" -o "$OUT/dashboard.png" \
  "$GRAFANA/render/d/$DASH?$COMMON&kiosk&width=1600&height=2000"
echo "saved $OUT/dashboard.png"
