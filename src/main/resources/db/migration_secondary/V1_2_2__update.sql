CREATE TABLE ev_history.wallet_track_settlement (

    id INT AUTO_INCREMENT PRIMARY KEY,

    transaction_id INT,
    station_id VARCHAR(50),
    cpo_id VARCHAR(10),
    station_name VARCHAR(100),
    station_city VARCHAR(50),
    station_state VARCHAR(50),

    id_tag VARCHAR(100),

    charger_id VARCHAR(50),
    charger_qr_code VARCHAR(100),
    con_no INT,

    start_energy DOUBLE,
    tariff_amount DOUBLE,
    gst_with_tariff_amount DOUBLE,
    last_energy DOUBLE,
    wallet_amount DOUBLE,

    consumed_energy DOUBLE,
    consumed_amount DOUBLE,
    total_consumed_amount DOUBLE,

    start_timestamp TIMESTAMP(6),
    stop_timestamp TIMESTAMP(6),

    is_active_transaction TINYINT(1) DEFAULT 1,

    dealer_unit_cost DOUBLE,
    dealer_total_amount DOUBLE,
    customer_share_amount DOUBLE,
    total_share_amount DOUBLE
);



CREATE TABLE ev_history.dealer_settlement_slab (
id INT NOT NULL AUTO_INCREMENT,
min_tariff DOUBLE,
max_tariff DOUBLE,
dealer_unit_cost DOUBLE,
PRIMARY KEY (id)
);