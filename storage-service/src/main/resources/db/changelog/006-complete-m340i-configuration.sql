INSERT INTO car_model_categories (car_model_id, category_id)
SELECT 'b0000000-0000-0000-0000-000000000003', category_id
  FROM (VALUES
        ('c0000000-0000-0000-0000-000000000001'),
        ('c0000000-0000-0000-0000-000000000002'),
        ('c0000000-0000-0000-0000-000000000003'),
        ('c0000000-0000-0000-0000-000000000004')) required(category_id)
 WHERE NOT EXISTS (
       SELECT 1
         FROM car_model_categories existing
        WHERE existing.car_model_id = 'b0000000-0000-0000-0000-000000000003'
          AND existing.category_id = required.category_id
 );

INSERT INTO variant_compatible_models (variant_id, car_model_id)
SELECT 'd0000000-0000-0000-0000-000000000004',
       'b0000000-0000-0000-0000-000000000003'
 WHERE NOT EXISTS (
       SELECT 1
         FROM variant_compatible_models existing
        WHERE existing.variant_id = 'd0000000-0000-0000-0000-000000000004'
          AND existing.car_model_id = 'b0000000-0000-0000-0000-000000000003'
 );
