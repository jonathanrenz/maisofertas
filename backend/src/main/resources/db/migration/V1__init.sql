CREATE TABLE deals (
    id                   UUID PRIMARY KEY,
    store                VARCHAR(32)   NOT NULL,
    title                VARCHAR(500)  NOT NULL,
    url                  VARCHAR(2048) NOT NULL,
    image_url            VARCHAR(2048),
    price                NUMERIC(12, 2) NOT NULL,
    original_price       NUMERIC(12, 2),
    source               VARCHAR(32)   NOT NULL,
    status               VARCHAR(32)   NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL,
    posted_telegram_at   TIMESTAMPTZ,
    posted_whatsapp_at   TIMESTAMPTZ
);

CREATE INDEX idx_deals_url ON deals (url);
CREATE INDEX idx_deals_status ON deals (status);
