-- ARMS schema (MySQL 8). Normalised to 3NF: every fact is stored once.
-- Run automatically at startup; every statement is idempotent (IF NOT EXISTS).

CREATE TABLE IF NOT EXISTS users (
    user_id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(30)  NOT NULL,
    full_name       VARCHAR(80)  NOT NULL,
    password_hash   VARCHAR(200) NOT NULL,                 -- PBKDF2 hash, never the password
    role            ENUM('CUSTOMER','ADMIN') NOT NULL DEFAULT 'CUSTOMER',
    failed_attempts INT          NOT NULL DEFAULT 0,       -- consecutive failed logins
    locked_until    DATETIME     NULL,                     -- set after 5 failures
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS airports (
    code CHAR(3)      PRIMARY KEY,                         -- IATA code, e.g. DEL
    city VARCHAR(60)  NOT NULL,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS flights (
    flight_id      BIGINT     AUTO_INCREMENT PRIMARY KEY,
    flight_no      VARCHAR(8) NOT NULL,
    origin         CHAR(3)    NOT NULL,
    destination    CHAR(3)    NOT NULL,
    departure_time DATETIME   NOT NULL,
    arrival_time   DATETIME   NOT NULL,
    status         ENUM('SCHEDULED','CANCELLED') NOT NULL DEFAULT 'SCHEDULED',
    CONSTRAINT fk_flight_origin FOREIGN KEY (origin) REFERENCES airports (code),
    CONSTRAINT fk_flight_destination FOREIGN KEY (destination) REFERENCES airports (code),
    CONSTRAINT chk_route CHECK (origin <> destination),
    CONSTRAINT chk_times CHECK (arrival_time > departure_time),
    CONSTRAINT uq_flight_departure UNIQUE (flight_no, departure_time),
    INDEX idx_route_date (origin, destination, departure_time)   -- serves the search query
);

-- One price per class per flight (replaces the old wide table with a column per class)
CREATE TABLE IF NOT EXISTS fares (
    flight_id  BIGINT        NOT NULL,
    fare_class ENUM('ECONOMY','BUSINESS','FIRST') NOT NULL,
    price      DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (flight_id, fare_class),
    CONSTRAINT fk_fare_flight FOREIGN KEY (flight_id) REFERENCES flights (flight_id) ON DELETE CASCADE,
    CONSTRAINT chk_price CHECK (price > 0)
);

-- Real seats (12A, 12B, ...) instead of a counter, so a seat can be chosen and shown on a map
CREATE TABLE IF NOT EXISTS seats (
    seat_id    BIGINT     AUTO_INCREMENT PRIMARY KEY,
    flight_id  BIGINT     NOT NULL,
    seat_no    VARCHAR(4) NOT NULL,
    fare_class ENUM('ECONOMY','BUSINESS','FIRST') NOT NULL,
    CONSTRAINT uq_seat UNIQUE (flight_id, seat_no),
    CONSTRAINT fk_seat_fare FOREIGN KEY (flight_id, fare_class)
        REFERENCES fares (flight_id, fare_class) ON DELETE CASCADE,
    INDEX idx_seat_class (flight_id, fare_class)
);

CREATE TABLE IF NOT EXISTS bookings (
    booking_id      BIGINT   AUTO_INCREMENT PRIMARY KEY,
    pnr             CHAR(6)  NOT NULL,
    user_id         BIGINT   NOT NULL,
    flight_id       BIGINT   NOT NULL,
    status          ENUM('HELD','CONFIRMED','CANCELLED','PAYMENT_FAILED','EXPIRED') NOT NULL,
    hold_expires_at DATETIME NULL,                         -- seats are held while payment happens
    booked_at       DATETIME NOT NULL,
    cancelled_at    DATETIME NULL,
    refund_amount   DECIMAL(10,2) NULL,
    CONSTRAINT uq_pnr UNIQUE (pnr),
    CONSTRAINT fk_booking_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_booking_flight FOREIGN KEY (flight_id) REFERENCES flights (flight_id),
    INDEX idx_booking_user (user_id, booked_at),
    INDEX idx_booking_hold (flight_id, status, hold_expires_at)
);

-- One row per passenger seat. The booking total is SUM(price), so it is never stored twice.
-- price is a snapshot of the fare at booking time: later fare changes don't rewrite history.
CREATE TABLE IF NOT EXISTS booking_seats (
    booking_id     BIGINT        NOT NULL,
    seat_id        BIGINT        NOT NULL,
    passenger_name VARCHAR(80)   NOT NULL,
    price          DECIMAL(10,2) NOT NULL,
    active         TINYINT       NULL DEFAULT 1,           -- 1 while this booking holds the seat, NULL once released
    PRIMARY KEY (booking_id, seat_id),
    -- The double-booking guarantee: at most ONE row per seat with active = 1.
    -- MySQL allows many NULLs in a unique key, so released rows never clash.
    CONSTRAINT uq_active_seat UNIQUE (seat_id, active),
    CONSTRAINT chk_active CHECK (active = 1 OR active IS NULL),
    CONSTRAINT fk_bs_booking FOREIGN KEY (booking_id) REFERENCES bookings (booking_id),
    CONSTRAINT fk_bs_seat FOREIGN KEY (seat_id) REFERENCES seats (seat_id)
);

-- Every charge and refund. Card numbers are never stored: only a masked label like "CARD ****1111".
CREATE TABLE IF NOT EXISTS payments (
    payment_id BIGINT        AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT        NOT NULL,
    type       ENUM('CHARGE','REFUND') NOT NULL,
    method     VARCHAR(40)   NOT NULL,
    amount     DECIMAL(10,2) NOT NULL,
    status     ENUM('SUCCESS','DECLINED') NOT NULL,
    reference  VARCHAR(40)   NULL,
    created_at DATETIME      NOT NULL,
    CONSTRAINT fk_payment_booking FOREIGN KEY (booking_id) REFERENCES bookings (booking_id),
    INDEX idx_payment_booking (booking_id)
);
