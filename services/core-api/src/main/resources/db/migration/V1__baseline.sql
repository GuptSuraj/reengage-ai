CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE app_user (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email VARCHAR(254) NOT NULL UNIQUE,
  phone_e164 VARCHAR(20) UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(80) NOT NULL,
  role VARCHAR(20) NOT NULL CHECK (role IN ('CUSTOMER','ADMIN')),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE user_preference (
  user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
  email_opt_in BOOLEAN NOT NULL DEFAULT FALSE,
  whatsapp_opt_in BOOLEAN NOT NULL DEFAULT FALSE,
  preferred_channel VARCHAR(20) NOT NULL DEFAULT 'EMAIL',
  timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Kolkata',
  quiet_start SMALLINT NOT NULL DEFAULT 21 CHECK (quiet_start BETWEEN 0 AND 23),
  quiet_end SMALLINT NOT NULL DEFAULT 8 CHECK (quiet_end BETWEEN 0 AND 23),
  blocked_categories JSONB NOT NULL DEFAULT '[]',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE product (
  id VARCHAR(80) PRIMARY KEY,
  name VARCHAR(160) NOT NULL,
  brand VARCHAR(80) NOT NULL,
  category VARCHAR(80) NOT NULL,
  description TEXT NOT NULL,
  price_inr INTEGER NOT NULL CHECK (price_inr >= 0),
  rating NUMERIC(2,1) NOT NULL CHECK (rating BETWEEN 0 AND 5),
  stock INTEGER NOT NULL CHECK (stock >= 0),
  quality_score NUMERIC(3,2) NOT NULL DEFAULT 1 CHECK (quality_score BETWEEN 0 AND 1),
  features JSONB NOT NULL DEFAULT '[]',
  accent VARCHAR(20) NOT NULL DEFAULT '#3159df',
  image_key VARCHAR(100),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  search_tsv TSVECTOR GENERATED ALWAYS AS
    (to_tsvector('english', coalesce(name,'') || ' ' || coalesce(brand,'') || ' ' ||
     coalesce(category,'') || ' ' || coalesce(description,''))) STORED,
  version BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_product_catalog ON product(category,brand,price_inr) WHERE active=true;
CREATE INDEX ix_product_search ON product USING GIN(search_tsv);
CREATE TABLE cart_item (
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  product_id VARCHAR(80) NOT NULL REFERENCES product(id),
  quantity INTEGER NOT NULL CHECK (quantity > 0),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(user_id,product_id)
);
CREATE TABLE purchase_order (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES app_user(id),
  idempotency_key VARCHAR(100) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'COMPLETED',
  total_inr INTEGER NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(user_id,idempotency_key)
);
CREATE INDEX ix_order_user_created ON purchase_order(user_id,created_at DESC);
CREATE TABLE order_item (
  order_id UUID NOT NULL REFERENCES purchase_order(id) ON DELETE CASCADE,
  product_id VARCHAR(80) NOT NULL REFERENCES product(id),
  product_name VARCHAR(160) NOT NULL,
  unit_price_inr INTEGER NOT NULL,
  quantity INTEGER NOT NULL,
  PRIMARY KEY(order_id,product_id)
);
CREATE TABLE behaviour_event (
  event_id UUID PRIMARY KEY,
  user_id UUID,
  anonymous_id VARCHAR(100),
  session_id UUID NOT NULL,
  event_type VARCHAR(50) NOT NULL,
  product_id VARCHAR(80),
  occurred_at TIMESTAMPTZ NOT NULL,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  source_page TEXT,
  device JSONB NOT NULL DEFAULT '{}',
  metadata JSONB NOT NULL DEFAULT '{}',
  schema_version SMALLINT NOT NULL DEFAULT 1,
  CHECK (user_id IS NOT NULL OR anonymous_id IS NOT NULL)
);
CREATE INDEX ix_event_user_occurred ON behaviour_event(user_id,occurred_at DESC);
CREATE INDEX ix_event_session ON behaviour_event(session_id,occurred_at);
CREATE INDEX ix_event_received ON behaviour_event(received_at DESC);
CREATE TABLE user_profile (
  user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
  profile JSONB NOT NULL DEFAULT '{}',
  intent_score NUMERIC(5,4) NOT NULL DEFAULT 0,
  intent_level VARCHAR(10) NOT NULL DEFAULT 'LOW',
  intent_signals JSONB NOT NULL DEFAULT '[]',
  recommendations JSONB NOT NULL DEFAULT '[]',
  model_version VARCHAR(40) NOT NULL DEFAULT 'rules-v1',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE notification_job (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES app_user(id),
  product_id VARCHAR(80) NOT NULL REFERENCES product(id),
  channel VARCHAR(20) NOT NULL,
  status VARCHAR(30) NOT NULL,
  intent_score NUMERIC(5,4) NOT NULL,
  template_key VARCHAR(80) NOT NULL,
  variant VARCHAR(20) NOT NULL DEFAULT 'A',
  message TEXT NOT NULL,
  recommendations JSONB NOT NULL DEFAULT '[]',
  scheduled_for TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  claimed_at TIMESTAMPTZ,
  sent_at TIMESTAMPTZ,
  opened_at TIMESTAMPTZ,
  clicked_at TIMESTAMPTZ,
  converted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  retry_count INTEGER NOT NULL DEFAULT 0,
  provider_message_id VARCHAR(160),
  failure_reason TEXT,
  policy_version VARCHAR(40) NOT NULL DEFAULT 'decision-v1',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_notification_active ON notification_job(user_id,product_id,template_key)
  WHERE status IN ('SCHEDULED','PROCESSING','SENT');
CREATE INDEX ix_notification_due ON notification_job(status,scheduled_for);
CREATE TABLE delivery_attempt (
  id BIGSERIAL PRIMARY KEY,
  notification_id UUID NOT NULL REFERENCES notification_job(id) ON DELETE CASCADE,
  attempt INTEGER NOT NULL,
  provider VARCHAR(40) NOT NULL,
  status VARCHAR(30) NOT NULL,
  response_code VARCHAR(40),
  error_message TEXT,
  duration_ms INTEGER,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(notification_id,attempt)
);
CREATE TABLE outbox_event (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  aggregate_type VARCHAR(60) NOT NULL,
  aggregate_id VARCHAR(100) NOT NULL,
  topic VARCHAR(100) NOT NULL,
  event_key VARCHAR(100) NOT NULL,
  payload JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ,
  locked_at TIMESTAMPTZ,
  locked_by VARCHAR(100),
  attempts INTEGER NOT NULL DEFAULT 0,
  last_error TEXT
);
CREATE INDEX ix_outbox_unpublished ON outbox_event(created_at) WHERE published_at IS NULL;
CREATE TABLE dead_letter (
  id BIGSERIAL PRIMARY KEY,
  source VARCHAR(100) NOT NULL,
  event_key VARCHAR(100),
  payload JSONB NOT NULL,
  error_class VARCHAR(160) NOT NULL,
  error_message TEXT,
  retry_count INTEGER NOT NULL DEFAULT 0,
  trace_id VARCHAR(64),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO product(id,name,brand,category,description,price_inr,rating,stock,features,accent,image_key) VALUES
('sony-wh-ch720n','Sony WH-CH720N','Sony','Headphones','Lightweight wireless noise-cancelling headphones with 35-hour battery life.',8990,4.6,18,'["Noise cancelling","35h battery","Multipoint","Lightweight"]','#3159df','headphones'),
('jbl-live-770nc','JBL Live 770NC','JBL','Headphones','Adaptive noise cancelling headphones with bold sound and long battery life.',9999,4.5,11,'["Adaptive ANC","65h battery","Spatial sound","Fast pair"]','#ef6c3c','headphones'),
('soundcore-q20i','Soundcore Q20i','Soundcore','Headphones','Hybrid active noise cancelling headphones with oversized dynamic drivers.',4999,4.4,23,'["Hybrid ANC","40h battery","Hi-res audio","App EQ"]','#16a085','headphones'),
('marshall-major-v','Marshall Major V','Marshall','Headphones','Iconic on-ear wireless headphones with more than 100 hours of playtime.',12999,4.7,7,'["100h battery","Wireless charging","Custom button","Foldable"]','#272523','headphones'),
('pixel-watch-3','Pixel Watch 3','Google','Wearables','An elegant smartwatch with advanced fitness and readiness insights.',32990,4.5,9,'["AMOLED","GPS","Heart rate","Sleep insights"]','#7c5cff','watch'),
('amazfit-active-2','Amazfit Active 2','Amazfit','Wearables','Slim everyday fitness watch with offline maps and long battery life.',9999,4.3,20,'["Offline maps","10-day battery","GPS","160 modes"]','#ba6a52','watch'),
('jbl-flip-6','JBL Flip 6','JBL','Speakers','Waterproof portable speaker with powerful, clear sound.',8999,4.7,16,'["IP67","12h battery","PartyBoost","Portable"]','#f05a28','speaker'),
('sony-ult-field-1','Sony ULT Field 1','Sony','Speakers','Compact waterproof speaker with enhanced bass and a rugged build.',10990,4.6,14,'["ULT bass","IP67","12h battery","Shockproof"]','#1664e8','speaker'),
('kindle-paperwhite','Kindle Paperwhite','Amazon','Readers','Glare-free reading with a warm adjustable light and weeks of battery.',14999,4.8,12,'["7-inch display","Waterproof","Warm light","16 GB"]','#4c7f7a','reader');
