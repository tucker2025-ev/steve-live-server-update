CREATE TABLE charger_status (
    charge_box_id VARCHAR(100) PRIMARY KEY,

    first_seen_time DATETIME NOT NULL,
    last_heartbeat_time DATETIME NOT NULL,

    status VARCHAR(10) NOT NULL DEFAULT 'ONLINE',

    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE charger_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    charge_box_id VARCHAR(100) NOT NULL,

    status VARCHAR(10) NOT NULL, -- ONLINE / OFFLINE

    start_time DATETIME NOT NULL,
    end_time DATETIME NULL,

    duration_seconds INT DEFAULT 0,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);