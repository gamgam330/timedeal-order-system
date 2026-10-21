.PHONY: up down reset run run-perf test secret-scan k6 k6-reset k6-check capture

GITLEAKS_IMAGE := zricethezav/gitleaks:v8.30.1
COMPOSE := docker compose -f infra/docker-compose.yml
# 컨테이너 안의 환경 변수로 접속한다 (비밀번호를 Makefile에 적지 않음)
MYSQL_EXEC := docker exec -i timedeal-mysql sh -c 'mysql -u"$$MYSQL_USER" -p"$$MYSQL_PASSWORD" timedeal 2>/dev/null'
MYSQL_TABLE := docker exec -i timedeal-mysql sh -c 'mysql -t -u"$$MYSQL_USER" -p"$$MYSQL_PASSWORD" timedeal 2>/dev/null'

# 측정용 JVM 옵션: 힙을 고정해야 실행마다 GC 동작이 같아진다
PERF_JAVA_OPTS := -Xms1g -Xmx1g
APP_JAR := app/build/libs/timedeal-0.0.1-SNAPSHOT.jar

# make k6 SCRIPT=step01-load LABEL=broken
SCRIPT ?= step01-load
LABEL ?= run
RUN_ID := $(SCRIPT)-$(LABEL)-$(shell date +%Y%m%d-%H%M)

up:          ## 인프라(MySQL, Prometheus, Grafana) 실행
	$(COMPOSE) up -d --wait

down:        ## 인프라 종료
	$(COMPOSE) down

reset:       ## 인프라 종료 + 볼륨 삭제 (DB, Prometheus 데이터 초기화)
	$(COMPOSE) down -v

run:         ## 애플리케이션 실행 (개발용)
	cd app && ./gradlew bootRun

run-perf:    ## 애플리케이션 실행 (부하 테스트용: jar + 고정 JVM 옵션)
	cd app && ./gradlew -q bootJar
	java $(PERF_JAVA_OPTS) -jar $(APP_JAR)

test:        ## 테스트 (Testcontainers로 MySQL을 띄운다)
	cd app && ./gradlew test

secret-scan: ## push 전에 반드시 실행: git 이력 전체와 작업 트리에서 비밀값을 검사한다
	docker run --rm -v "$(CURDIR)":/repo $(GITLEAKS_IMAGE) git /repo --redact -v
	docker run --rm -v "$(CURDIR)":/repo $(GITLEAKS_IMAGE) dir /repo --redact -v

k6-reset:    ## 부하 테스트 데이터 초기화 (주문/결제 삭제, 재고 복구)
	$(MYSQL_EXEC) < k6/sql/reset.sql

k6-check:    ## 정합성 검사: 남은 재고 + 판매 수량 = 초기 재고 인가?
	$(MYSQL_TABLE) < k6/sql/check.sql

k6: k6-reset ## 부하 테스트 실행 → 결과를 k6/results/에 저장하고 Prometheus로 전송
	K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
	K6_PROMETHEUS_RW_TREND_STATS='p(95),p(99),avg,max' \
	k6 run -o experimental-prometheus-rw --tag testid=$(RUN_ID) \
		--summary-export k6/results/$(RUN_ID).json \
		k6/scripts/$(SCRIPT).js 2>&1 | tee k6/results/$(RUN_ID).txt; true
	@echo "\n=== 정합성 검사 ($(RUN_ID)) ===" | tee -a k6/results/$(RUN_ID).txt
	@$(MYSQL_TABLE) < k6/sql/check.sql | tee -a k6/results/$(RUN_ID).txt

capture:     ## Grafana 패널을 PNG로 저장: make capture NAME=step01-load FROM="2026-10-21 19:50:45" TO="2026-10-21 19:56:30"
	scripts/capture-grafana.sh "$(NAME)" "$(FROM)" "$(TO)"
