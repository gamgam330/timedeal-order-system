-- MySQL이 서버 시작 뒤 실행한 문장 수 (누적값). make k6가 테스트 전후로 읽어서 차이를 낸다 (04단계)
-- 품절 요청이 DB에 몇 번 닿았는지 보려는 용도다
SHOW GLOBAL STATUS WHERE Variable_name IN ('Com_select', 'Com_update', 'Com_insert', 'Com_commit', 'Com_rollback');
