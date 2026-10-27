-- MySQL 서버 시작 뒤 누적값. make k6가 테스트 전후로 읽어서 차이를 낸다 (04단계)
-- Com_*: 실행한 문장 수 (품절 요청이 DB에 몇 번 닿았는지)
-- Innodb_row_lock_waits: 행 락을 기다린 횟수, Innodb_row_lock_time: 기다린 시간 합계(ms)
SHOW GLOBAL STATUS WHERE Variable_name IN ('Com_select', 'Com_update', 'Com_insert', 'Com_commit', 'Com_rollback', 'Innodb_row_lock_waits', 'Innodb_row_lock_time');
