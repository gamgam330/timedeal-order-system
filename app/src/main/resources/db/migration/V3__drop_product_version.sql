-- 02단계: 낙관적 락 실험(V2)이 끝나고 원자적 UPDATE를 채택했으므로 버전 컬럼을 지운다. (troubleshooting 002)
ALTER TABLE product DROP COLUMN version;
