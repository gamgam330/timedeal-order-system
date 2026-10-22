-- 02단계: 낙관적 락용 버전 컬럼. 수정할 때마다 1씩 오르고, UPDATE ... WHERE version = ? 로 충돌을 감지한다.
ALTER TABLE product ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
