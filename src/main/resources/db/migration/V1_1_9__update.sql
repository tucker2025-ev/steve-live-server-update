CREATE TABLE transaction_energy_mismatch_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    transaction_id INT NOT NULL,

    current_value DOUBLE NOT NULL,
    current_value_timestamp TIMESTAMP NOT NULL,

    previous_value DOUBLE NOT NULL,
    previous_value_timestamp TIMESTAMP NOT NULL,

    charge_box_id VARCHAR(50) DEFAULT NULL,
    connector_pk INT DEFAULT NULL,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_transaction_id (transaction_id),
    INDEX idx_charge_box (charge_box_id),
    INDEX idx_created_at (created_at)
);