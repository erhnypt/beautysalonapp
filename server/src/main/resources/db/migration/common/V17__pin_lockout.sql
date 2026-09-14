-- Güvenlik gözden geçirme A7: kritik rollerde 2. faktör (PIN) akışı. `pin_hash`
-- V1'den beri şemada var ama akışsızdı. Burada yalnızca PIN brute-force kilidi
-- için gereken sayaç/zaman alanları eklenir (parola kilidiyle aynı desen).
ALTER TABLE app_user ADD COLUMN pin_failed_count INT NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN pin_locked_until TIMESTAMP;
