.PHONY: up down reset run test

up:        ## 인프라(MySQL) 실행
	docker compose -f infra/docker-compose.yml up -d --wait

down:      ## 인프라 종료
	docker compose -f infra/docker-compose.yml down

reset:     ## 인프라 종료 + 볼륨 삭제 (DB 초기화)
	docker compose -f infra/docker-compose.yml down -v

run:       ## 애플리케이션 실행
	cd app && ./gradlew bootRun

test:      ## 테스트 (Testcontainers로 MySQL을 띄운다)
	cd app && ./gradlew test
