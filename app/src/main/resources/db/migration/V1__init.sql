-- PK는 전부 BIGINT AUTO_INCREMENT로 시작한다 (02단계에서 UUID 계열과 비교 예정)
-- 성능용 세컨더리 인덱스는 일부러 넣지 않는다 (02단계에서 측정한 뒤 추가)
-- 물리 FK는 걸지 않는다 (08단계 서비스 분리, 그리고 자식 INSERT 시 부모 행 S락 문제)

CREATE TABLE product
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    price      BIGINT       NOT NULL,
    stock      INT          NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE orders
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    product_id  BIGINT      NOT NULL,
    quantity    INT         NOT NULL,
    total_price BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE payment
(
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    order_id          BIGINT      NOT NULL,
    amount            BIGINT      NOT NULL,
    status            VARCHAR(20) NOT NULL,
    pg_transaction_id VARCHAR(64) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- 무결성 제약: 주문 하나에 결제는 하나뿐이다 (성능용 인덱스가 아님)
    CONSTRAINT uk_payment_order_id UNIQUE (order_id)
) ENGINE = InnoDB;

-- 타임딜 상품: 재고 100개
INSERT INTO product (name, price, stock, created_at)
VALUES ('타임딜 한정판 기계식 키보드', 10000, 100, NOW(6));
