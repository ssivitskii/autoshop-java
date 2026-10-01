CREATE TABLE cars
(
    id                       UUID PRIMARY KEY         NOT NULL,
    brand                    VARCHAR(255)             NOT NULL,
    model_name               VARCHAR(255)             NOT NULL,
    body_type                VARCHAR(50)              NOT NULL,
    fuel_type                VARCHAR(50)              NOT NULL,
    engine_power_hp          INT                      NOT NULL,
    engine_volume_liters     DOUBLE PRECISION         NOT NULL,
    transmission_type        VARCHAR(50)              NOT NULL,
    drive_type               VARCHAR(50)              NOT NULL,
    color                    VARCHAR(50)              NOT NULL,
    price                    DECIMAL(15, 2)           NOT NULL,
    available                BOOLEAN                  NOT NULL DEFAULT TRUE,
    available_for_test_drive BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    removed                  BOOLEAN                  NOT NULL DEFAULT FALSE
);

CREATE TABLE car_models
(
    id         UUID PRIMARY KEY         NOT NULL,
    brand      VARCHAR(255)             NOT NULL,
    model_name VARCHAR(255)             NOT NULL,
    base_price DECIMAL(15, 2)           NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    removed    BOOLEAN                  NOT NULL DEFAULT FALSE
);

CREATE TABLE car_model_categories
(
    car_model_id UUID         NOT NULL,
    category_id  VARCHAR(255) NOT NULL,
    CONSTRAINT fk_car_model_categories_car_model FOREIGN KEY (car_model_id) REFERENCES car_models (id)
);

CREATE TABLE component_categories
(
    id          UUID PRIMARY KEY         NOT NULL,
    name        VARCHAR(255)             NOT NULL,
    description VARCHAR(1000),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    removed     BOOLEAN                  NOT NULL DEFAULT FALSE
);

CREATE TABLE component_variants
(
    id               UUID PRIMARY KEY         NOT NULL,
    name             VARCHAR(255)             NOT NULL,
    category_id      VARCHAR(255)             NOT NULL,
    price_adjustment DECIMAL(15, 2)           NOT NULL,
    is_base          BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    removed          BOOLEAN                  NOT NULL DEFAULT FALSE
);

CREATE TABLE variant_compatible_models
(
    variant_id   UUID         NOT NULL,
    car_model_id VARCHAR(255) NOT NULL,
    CONSTRAINT fk_variant_compatible_models_variant FOREIGN KEY (variant_id) REFERENCES component_variants (id)
);

CREATE TABLE spare_parts
(
    id                UUID PRIMARY KEY         NOT NULL,
    name              VARCHAR(255)             NOT NULL,
    manufacturer      VARCHAR(255),
    part_number       VARCHAR(255)             NOT NULL,
    price             DECIMAL(15, 2)           NOT NULL,
    quantity_in_stock INT                      NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    removed           BOOLEAN                  NOT NULL DEFAULT FALSE
);

CREATE TABLE spare_part_compatible_models
(
    spare_part_id UUID         NOT NULL,
    model_name    VARCHAR(255) NOT NULL,
    CONSTRAINT fk_spare_part_compatible_models_part FOREIGN KEY (spare_part_id) REFERENCES spare_parts (id)
);

CREATE TABLE assembly_orders
(
    id              UUID PRIMARY KEY         NOT NULL,
    source_order_id VARCHAR(255)             NOT NULL,
    order_type      VARCHAR(50)              NOT NULL,
    trace_id        VARCHAR(255),
    status          VARCHAR(50)              NOT NULL DEFAULT 'CREATED',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    removed         BOOLEAN                  NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_assembly_source_order ON assembly_orders (source_order_id) WHERE removed = FALSE;
