CREATE TABLE test_bench_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    charge_box_id VARCHAR(50),
    event VARCHAR(100),
    direction VARCHAR(20),
    message TEXT,
    time_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);