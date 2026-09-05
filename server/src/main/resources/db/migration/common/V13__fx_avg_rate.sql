-- Faz 8 mali kural düzeltmesi: döviz alım-satımı (plan §10.6). Kur farkının doğru
-- hesaplanabilmesi için döviz hesabının ağırlıklı ortalama kurunu saklıyoruz
-- (stock_level.avg_cost ile aynı yaklaşım).

ALTER TABLE fin_account ADD COLUMN avg_rate NUMERIC(19,6);
