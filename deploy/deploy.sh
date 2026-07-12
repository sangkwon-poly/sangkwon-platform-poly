#!/usr/bin/env bash
# 무중단 롤링 재배포: 새 이미지를 빌드한 뒤 app1 -> app2 순서로 한 대씩 교체한다.
# 한 대가 내려가는 동안 nginx가 나머지 인스턴스로 라우팅하므로 서비스가 끊기지 않는다.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "[1/3] 새 이미지 빌드"
docker compose build

for svc in app1 app2; do
  echo "[2/3] $svc 교체(나머지 인스턴스가 서빙 중)"
  docker compose up -d --no-deps --force-recreate "$svc"
  echo "      $svc 헬스 대기..."
  ok=0
  for i in $(seq 1 24); do
    if docker compose exec -T "$svc" curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1; then
      echo "      $svc UP"; ok=1; break
    fi
    sleep 5
  done
  if [ "$ok" != "1" ]; then
    echo "      [경고] $svc 가 헬스체크를 통과하지 못했습니다. 로그를 확인하세요: docker compose logs $svc"
    exit 1
  fi
done

echo "[3/3] 롤링 배포 완료. nginx 재적재"
docker compose exec -T nginx nginx -s reload || true
echo "done."
