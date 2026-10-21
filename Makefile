.PHONY: up down reset run test secret-scan

GITLEAKS_IMAGE := zricethezav/gitleaks:v8.30.1

up:          ## 인프라(MySQL, Prometheus, Grafana) 실행
	docker compose -f infra/docker-compose.yml up -d --wait

down:        ## 인프라 종료
	docker compose -f infra/docker-compose.yml down

reset:       ## 인프라 종료 + 볼륨 삭제 (DB, Prometheus 데이터 초기화)
	docker compose -f infra/docker-compose.yml down -v

run:         ## 애플리케이션 실행
	cd app && ./gradlew bootRun

test:        ## 테스트 (Testcontainers로 MySQL을 띄운다)
	cd app && ./gradlew test

secret-scan: ## push 전에 반드시 실행: git 이력 전체와 작업 트리에서 비밀값을 검사한다
	docker run --rm -v "$(CURDIR)":/repo $(GITLEAKS_IMAGE) git /repo --redact -v
	docker run --rm -v "$(CURDIR)":/repo $(GITLEAKS_IMAGE) dir /repo --redact -v
