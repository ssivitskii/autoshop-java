INSERT INTO component_categories (id, name, description, created_at, updated_at, removed)
VALUES ('c0000000-0000-0000-0000-000000000001', 'Колёса', 'Тип и размер колёс', now(), now(), false);
INSERT INTO component_categories (id, name, description, created_at, updated_at, removed)
VALUES ('c0000000-0000-0000-0000-000000000002', 'Трансмиссия', 'Тип коробки передач', now(), now(), false);
INSERT INTO component_categories (id, name, description, created_at, updated_at, removed)
VALUES ('c0000000-0000-0000-0000-000000000003', 'Руль', 'Тип рулевого колеса', now(), now(), false);
INSERT INTO component_categories (id, name, description, created_at, updated_at, removed)
VALUES ('c0000000-0000-0000-0000-000000000004', 'Интерьер салона', 'Материалы и стиль салона', now(), now(), false);

INSERT INTO car_models (id, brand, model_name, base_price, created_at, updated_at, removed)
VALUES ('b0000000-0000-0000-0000-000000000001', 'BMW', '320i', 3500000, now(), now(), false);
INSERT INTO car_models (id, brand, model_name, base_price, created_at, updated_at, removed)
VALUES ('b0000000-0000-0000-0000-000000000002', 'BMW', '330i', 4200000, now(), now(), false);
INSERT INTO car_models (id, brand, model_name, base_price, created_at, updated_at, removed)
VALUES ('b0000000-0000-0000-0000-000000000003', 'BMW', 'M340i', 5500000, now(), now(), false);

INSERT INTO car_model_categories (car_model_id, category_id)
VALUES ('b0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001');
INSERT INTO car_model_categories (car_model_id, category_id)
VALUES ('b0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000002');
INSERT INTO car_model_categories (car_model_id, category_id)
VALUES ('b0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000003');
INSERT INTO car_model_categories (car_model_id, category_id)
VALUES ('b0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000004');
INSERT INTO car_model_categories (car_model_id, category_id)
VALUES ('b0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000001');
INSERT INTO car_model_categories (car_model_id, category_id)
VALUES ('b0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000002');
INSERT INTO car_model_categories (car_model_id, category_id)
VALUES ('b0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000003');
INSERT INTO car_model_categories (car_model_id, category_id)
VALUES ('b0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000004');

INSERT INTO component_variants (id, name, category_id, price_adjustment, is_base, created_at, updated_at, removed)
VALUES ('d0000000-0000-0000-0000-000000000001', '17'' Standard', 'c0000000-0000-0000-0000-000000000001', 0, true, now(),
        now(), false);
INSERT INTO component_variants (id, name, category_id, price_adjustment, is_base, created_at, updated_at, removed)
VALUES ('d0000000-0000-0000-0000-000000000002', '19'' M-Sport', 'c0000000-0000-0000-0000-000000000001', 95000, false,
        now(), now(), false);
INSERT INTO component_variants (id, name, category_id, price_adjustment, is_base, created_at, updated_at, removed)
VALUES ('d0000000-0000-0000-0000-000000000003', '18'' Aero', 'c0000000-0000-0000-0000-000000000001', 45000, false,
        now(), now(), false);
INSERT INTO component_variants (id, name, category_id, price_adjustment, is_base, created_at, updated_at, removed)
VALUES ('d0000000-0000-0000-0000-000000000004', 'Автоматическая 8AT', 'c0000000-0000-0000-0000-000000000002', 0, true,
        now(), now(), false);
INSERT INTO component_variants (id, name, category_id, price_adjustment, is_base, created_at, updated_at, removed)
VALUES ('d0000000-0000-0000-0000-000000000005', 'Механическая 6MT', 'c0000000-0000-0000-0000-000000000002', -30000,
        false, now(), now(), false);
INSERT INTO component_variants (id, name, category_id, price_adjustment, is_base, created_at, updated_at, removed)
VALUES ('d0000000-0000-0000-0000-000000000006', 'Спортивный кожаный', 'c0000000-0000-0000-0000-000000000003', 0, true,
        now(), now(), false);
INSERT INTO component_variants (id, name, category_id, price_adjustment, is_base, created_at, updated_at, removed)
VALUES ('d0000000-0000-0000-0000-000000000007', 'M-Sport с подогревом', 'c0000000-0000-0000-0000-000000000003', 25000,
        false, now(), now(), false);
INSERT INTO component_variants (id, name, category_id, price_adjustment, is_base, created_at, updated_at, removed)
VALUES ('d0000000-0000-0000-0000-000000000008', 'Тканевый Graphite', 'c0000000-0000-0000-0000-000000000004', 0, true,
        now(), now(), false);
INSERT INTO component_variants (id, name, category_id, price_adjustment, is_base, created_at, updated_at, removed)
VALUES ('d0000000-0000-0000-0000-000000000009', 'Кожаный Dakota', 'c0000000-0000-0000-0000-000000000004', 110000, false,
        now(), now(), false);
INSERT INTO component_variants (id, name, category_id, price_adjustment, is_base, created_at, updated_at, removed)
VALUES ('d0000000-0000-0000-0000-000000000010', 'Спортивный Performance', 'c0000000-0000-0000-0000-000000000004',
        160000, false, now(), now(), false);

INSERT INTO variant_compatible_models (variant_id, car_model_id)
VALUES ('d0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001');
INSERT INTO variant_compatible_models (variant_id, car_model_id)
VALUES ('d0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000001');
INSERT INTO variant_compatible_models (variant_id, car_model_id)
VALUES ('d0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000002');
INSERT INTO variant_compatible_models (variant_id, car_model_id)
VALUES ('d0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000003');
INSERT INTO variant_compatible_models (variant_id, car_model_id)
VALUES ('d0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000001');
INSERT INTO variant_compatible_models (variant_id, car_model_id)
VALUES ('d0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000002');
INSERT INTO variant_compatible_models (variant_id, car_model_id)
VALUES ('d0000000-0000-0000-0000-000000000004', 'b0000000-0000-0000-0000-000000000001');
INSERT INTO variant_compatible_models (variant_id, car_model_id)
VALUES ('d0000000-0000-0000-0000-000000000004', 'b0000000-0000-0000-0000-000000000002');
INSERT INTO variant_compatible_models (variant_id, car_model_id)
VALUES ('d0000000-0000-0000-0000-000000000005', 'b0000000-0000-0000-0000-000000000001');
INSERT INTO variant_compatible_models (variant_id, car_model_id)
VALUES ('d0000000-0000-0000-0000-000000000005', 'b0000000-0000-0000-0000-000000000002');
INSERT INTO variant_compatible_models (variant_id, car_model_id)
VALUES ('d0000000-0000-0000-0000-000000000006', 'b0000000-0000-0000-0000-000000000001');
INSERT INTO variant_compatible_models (variant_id, car_model_id)
VALUES ('d0000000-0000-0000-0000-000000000006', 'b0000000-0000-0000-0000-000000000002');
INSERT INTO variant_compatible_models (variant_id, car_model_id)
VALUES ('d0000000-0000-0000-0000-000000000007', 'b0000000-0000-0000-0000-000000000001');
INSERT INTO variant_compatible_models (variant_id, car_model_id)
VALUES ('d0000000-0000-0000-0000-000000000007', 'b0000000-0000-0000-0000-000000000002');
INSERT INTO variant_compatible_models (variant_id, car_model_id)
VALUES ('d0000000-0000-0000-0000-000000000007', 'b0000000-0000-0000-0000-000000000003');
INSERT INTO variant_compatible_models (variant_id, car_model_id)
VALUES ('d0000000-0000-0000-0000-000000000008', 'b0000000-0000-0000-0000-000000000001');
INSERT INTO variant_compatible_models (variant_id, car_model_id)
VALUES ('d0000000-0000-0000-0000-000000000009', 'b0000000-0000-0000-0000-000000000001');
INSERT INTO variant_compatible_models (variant_id, car_model_id)
VALUES ('d0000000-0000-0000-0000-000000000009', 'b0000000-0000-0000-0000-000000000002');
INSERT INTO variant_compatible_models (variant_id, car_model_id)
VALUES ('d0000000-0000-0000-0000-000000000010', 'b0000000-0000-0000-0000-000000000002');
INSERT INTO variant_compatible_models (variant_id, car_model_id)
VALUES ('d0000000-0000-0000-0000-000000000010', 'b0000000-0000-0000-0000-000000000003');

INSERT INTO cars (id, brand, model_name, body_type, fuel_type, engine_power_hp, engine_volume_liters, transmission_type,
                  drive_type, color, price, available, available_for_test_drive, created_at, updated_at, removed)
VALUES ('e0000000-0000-0000-0000-000000000001', 'BMW', '320i', 'SEDAN', 'PETROL', 184, 2.0, 'AUTOMATIC', 'REAR',
        'WHITE', 3500000, true, true, now(), now(), false);
INSERT INTO cars (id, brand, model_name, body_type, fuel_type, engine_power_hp, engine_volume_liters, transmission_type,
                  drive_type, color, price, available, available_for_test_drive, created_at, updated_at, removed)
VALUES ('e0000000-0000-0000-0000-000000000002', 'BMW', '330i', 'SEDAN', 'PETROL', 258, 2.0, 'AUTOMATIC', 'REAR',
        'BLACK', 4200000, true, false, now(), now(), false);

INSERT INTO spare_parts (id, name, manufacturer, part_number, price, quantity_in_stock, created_at, updated_at, removed)
VALUES ('f0000000-0000-0000-0000-000000000001', 'Масляный фильтр', 'MANN-FILTER', 'HU 816 x', 850, 50, now(), now(),
        false);
INSERT INTO spare_parts (id, name, manufacturer, part_number, price, quantity_in_stock, created_at, updated_at, removed)
VALUES ('f0000000-0000-0000-0000-000000000002', 'Тормозные колодки передние', 'Brembo', 'P 06 075', 4500, 20, now(),
        now(), false);

INSERT INTO spare_part_compatible_models (spare_part_id, model_name)
VALUES ('f0000000-0000-0000-0000-000000000001', 'BMW 320i');
INSERT INTO spare_part_compatible_models (spare_part_id, model_name)
VALUES ('f0000000-0000-0000-0000-000000000001', 'BMW 330i');
INSERT INTO spare_part_compatible_models (spare_part_id, model_name)
VALUES ('f0000000-0000-0000-0000-000000000002', 'BMW 320i');
