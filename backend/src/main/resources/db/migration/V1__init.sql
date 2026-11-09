CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL
);

-- 로그아웃된 access token(jti)을 서버에서 거부하기 위한 목록
CREATE TABLE revoked_tokens (
    jti VARCHAR(64) PRIMARY KEY,
    expires_at DATETIME(6) NOT NULL
);

CREATE TABLE broadcasts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    category VARCHAR(30),
    status VARCHAR(10) NOT NULL,
    source_type VARCHAR(10),
    room_name VARCHAR(64) NOT NULL UNIQUE,
    started_at DATETIME(6) NULL,
    ended_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_broadcast_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);
CREATE INDEX idx_broadcast_status ON broadcasts (status);

CREATE TABLE comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    broadcast_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_comment_broadcast FOREIGN KEY (broadcast_id) REFERENCES broadcasts (id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_author FOREIGN KEY (author_id) REFERENCES users (id)
);
CREATE INDEX idx_comment_broadcast ON comments (broadcast_id, id);

CREATE TABLE donations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL UNIQUE,
    payment_key VARCHAR(200) NULL UNIQUE,
    broadcast_id BIGINT NOT NULL,
    donor_id BIGINT NOT NULL,
    streamer_id BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    message VARCHAR(200),
    status VARCHAR(10) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    approved_at DATETIME(6) NULL,
    CONSTRAINT fk_donation_broadcast FOREIGN KEY (broadcast_id) REFERENCES broadcasts (id) ON DELETE CASCADE,
    CONSTRAINT fk_donation_donor FOREIGN KEY (donor_id) REFERENCES users (id),
    CONSTRAINT fk_donation_streamer FOREIGN KEY (streamer_id) REFERENCES users (id)
);
CREATE INDEX idx_donation_broadcast ON donations (broadcast_id, status);

CREATE TABLE recordings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    broadcast_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    file_path VARCHAR(300) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    size_bytes BIGINT NOT NULL,
    chunk_count INT NOT NULL DEFAULT 0,
    status VARCHAR(10) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NULL,
    CONSTRAINT fk_recording_broadcast FOREIGN KEY (broadcast_id) REFERENCES broadcasts (id) ON DELETE CASCADE,
    CONSTRAINT fk_recording_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);
