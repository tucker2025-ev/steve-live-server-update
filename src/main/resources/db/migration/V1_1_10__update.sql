CREATE TABLE transaction_connector_energy (
    id INT AUTO_INCREMENT PRIMARY KEY,
    transaction_id INT UNSIGNED,
    charge_box_id VARCHAR(50),
    connector_id INT,
    energy_value VARCHAR(50),
    value_timestamp  timestamp(6)
);