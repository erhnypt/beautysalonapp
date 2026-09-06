package com.beautysalonapp.modules.stock.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/**
 * Stok raporları (plan §10.1): "Stok durum, hareket dökümü, kritik stok, devir hızı,
 * ölü stok, envanter değeri, depo/vitrin karşılaştırma." Raporlar okuma modelinden
 * çalışır — {@link JdbcTemplate} ile doğrudan sorgu (bkz. {@code ReportService}, aynı
 * yaklaşım). Tüm branch filtreleri denetlenmiş {@code Long}'dan üretilir, kullanıcı
 * girdisi asla ham SQL'e karışmaz.
 */
@Service
public class StockReportService {

    private final JdbcTemplate jdbc;

    public StockReportService(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public record StockStatusRow(long itemId, String itemCode, String itemName,
                                 long warehouseId, String warehouseCode, String warehouseType,
                                 BigDecimal qtyBase, BigDecimal avgCost, BigDecimal value) {}

    /** Stok durumu: her ürün × depo satırı, mevcut miktar ve değer. */
    public List<StockStatusRow> stockStatus(Long warehouseId, Long branchId) {
        return jdbc.query("""
                select i.id item_id, i.code item_code, i.name item_name,
                       w.id warehouse_id, w.code warehouse_code, w.wh_type warehouse_type,
                       sl.qty_base, sl.avg_cost, (sl.qty_base * sl.avg_cost) as stock_value
                from stock_level sl
                  join item i on i.id = sl.item_id
                  join warehouse w on w.id = sl.warehouse_id
                where i.deleted = false""" + bf("i.branch_id", branchId)
                        + (warehouseId == null ? "" : " and w.id = " + warehouseId) + """

                order by i.name, w.code
                """,
                (rs, n) -> new StockStatusRow(rs.getLong("item_id"), rs.getString("item_code"),
                        rs.getString("item_name"), rs.getLong("warehouse_id"), rs.getString("warehouse_code"),
                        rs.getString("warehouse_type"), rs.getBigDecimal("qty_base"), rs.getBigDecimal("avg_cost"),
                        rs.getBigDecimal("stock_value")));
    }

    public record MovementRow(long id, LocalDate date, long itemId, String itemCode, String itemName,
                              long warehouseId, String warehouseCode, String direction,
                              BigDecimal baseQty, BigDecimal unitCost, String docType, String docRef, String note) {}

    /** Hareket dökümü: tarih aralığı + isteğe bağlı ürün/depo filtresi. */
    public List<MovementRow> movements(LocalDate from, LocalDate to, Long itemId, Long warehouseId, Long branchId) {
        return jdbc.query("""
                select m.id, m.mv_date, m.item_id, i.code item_code, i.name item_name,
                       m.warehouse_id, w.code warehouse_code, m.direction, m.base_qty, m.unit_cost,
                       m.doc_type, m.doc_ref, m.note
                from stock_movement m
                  join item i on i.id = m.item_id
                  join warehouse w on w.id = m.warehouse_id
                where m.deleted = false""" + bf("m.branch_id", branchId)
                        + (from == null ? "" : " and m.mv_date >= ?")
                        + (to == null ? "" : " and m.mv_date <= ?")
                        + (itemId == null ? "" : " and m.item_id = " + itemId)
                        + (warehouseId == null ? "" : " and m.warehouse_id = " + warehouseId) + """

                order by m.mv_date desc, m.id desc
                """,
                (rs, n) -> new MovementRow(rs.getLong("id"), rs.getDate("mv_date").toLocalDate(),
                        rs.getLong("item_id"), rs.getString("item_code"), rs.getString("item_name"),
                        rs.getLong("warehouse_id"), rs.getString("warehouse_code"), rs.getString("direction"),
                        rs.getBigDecimal("base_qty"), rs.getBigDecimal("unit_cost"),
                        rs.getString("doc_type"), rs.getString("doc_ref"), rs.getString("note")),
                dateArgs(from, to));
    }

    public record CriticalStockRow(long itemId, String itemCode, String itemName,
                                   BigDecimal onHand, BigDecimal reorderLevel) {}

    /** Kritik stok: toplam (tüm depolar) miktarı yeniden sipariş seviyesinin altında/eşit ürünler. */
    public List<CriticalStockRow> criticalStock(Long branchId) {
        return jdbc.query("""
                select i.id item_id, i.code item_code, i.name item_name, i.reorder_level,
                       coalesce(sum(sl.qty_base), 0) as on_hand
                from item i
                  left join stock_level sl on sl.item_id = i.id
                  left join warehouse w on w.id = sl.warehouse_id
                where i.reorder_level is not null and i.deleted = false""" + bf("i.branch_id", branchId) + """

                group by i.id, i.code, i.name, i.reorder_level
                having coalesce(sum(sl.qty_base), 0) <= i.reorder_level
                order by i.name
                """,
                (rs, n) -> new CriticalStockRow(rs.getLong("item_id"), rs.getString("item_code"),
                        rs.getString("item_name"), rs.getBigDecimal("on_hand"), rs.getBigDecimal("reorder_level")));
    }

    public record TurnoverRow(long itemId, String itemCode, String itemName,
                              BigDecimal issuedQty, BigDecimal currentOnHand, BigDecimal turnoverRatio) {}

    /**
     * Devir hızı: dönem içindeki toplam çıkış miktarı / mevcut stok miktarı. Not: günlük
     * geçmiş bakiye tutulmadığından "ortalama stok" yerine **mevcut** stok kullanılır —
     * bu bir yaklaşıklıktır, kesin muhasebe devir hızı değildir.
     */
    public List<TurnoverRow> turnover(LocalDate from, LocalDate to, Long branchId) {
        return jdbc.query("""
                select i.id item_id, i.code item_code, i.name item_name,
                       coalesce(sum(case when m.direction = 'OUT' and m.mv_date between ? and ?
                                     then m.base_qty else 0 end), 0) as issued_qty,
                       coalesce((select sum(sl.qty_base) from stock_level sl where sl.item_id = i.id), 0) as on_hand
                from item i
                  left join stock_movement m on m.item_id = i.id and m.deleted = false
                where i.deleted = false and i.item_type = 'EMTIA'""" + bf("i.branch_id", branchId) + """

                group by i.id, i.code, i.name
                having coalesce(sum(case when m.direction = 'OUT' and m.mv_date between ? and ?
                                     then m.base_qty else 0 end), 0) > 0
                order by issued_qty desc
                """,
                (rs, n) -> {
                    BigDecimal issued = rs.getBigDecimal("issued_qty");
                    BigDecimal onHand = rs.getBigDecimal("on_hand");
                    BigDecimal ratio = onHand.signum() > 0
                            ? issued.divide(onHand, 4, RoundingMode.HALF_UP) : null;
                    return new TurnoverRow(rs.getLong("item_id"), rs.getString("item_code"),
                            rs.getString("item_name"), issued, onHand, ratio);
                },
                Date.valueOf(from), Date.valueOf(to), Date.valueOf(from), Date.valueOf(to));
    }

    public record DeadStockRow(long itemId, String itemCode, String itemName,
                               BigDecimal onHand, LocalDate lastMovementDate, Long daysSinceLastMovement) {}

    /** Ölü stok: elde miktarı > 0 olup son {@code idleDays} gündür hiç hareket görmeyen ürünler. */
    public List<DeadStockRow> deadStock(int idleDays, Long branchId) {
        LocalDate threshold = LocalDate.now().minusDays(idleDays);
        return jdbc.query("""
                select i.id item_id, i.code item_code, i.name item_name,
                       coalesce(sum(sl.qty_base), 0) as on_hand,
                       (select max(m.mv_date) from stock_movement m
                        where m.item_id = i.id and m.deleted = false) as last_mv
                from item i
                  left join stock_level sl on sl.item_id = i.id
                where i.deleted = false and i.item_type = 'EMTIA'""" + bf("i.branch_id", branchId) + """

                group by i.id, i.code, i.name
                having coalesce(sum(sl.qty_base), 0) > 0
                   and ((select max(m.mv_date) from stock_movement m
                         where m.item_id = i.id and m.deleted = false) is null
                        or (select max(m.mv_date) from stock_movement m
                            where m.item_id = i.id and m.deleted = false) <= ?)
                order by on_hand desc
                """,
                (rs, n) -> {
                    Date last = rs.getDate("last_mv");
                    LocalDate lastDate = last == null ? null : last.toLocalDate();
                    Long days = lastDate == null ? null : java.time.temporal.ChronoUnit.DAYS.between(lastDate, LocalDate.now());
                    return new DeadStockRow(rs.getLong("item_id"), rs.getString("item_code"),
                            rs.getString("item_name"), rs.getBigDecimal("on_hand"), lastDate, days);
                },
                Date.valueOf(threshold));
    }

    public record InventoryValueRow(long warehouseId, String warehouseCode, String warehouseName,
                                    String warehouseType, BigDecimal totalQty, BigDecimal totalValue) {}

    /** Envanter değeri: depo bazlı toplam miktar ve değer (miktar × ağırlıklı ortalama maliyet). */
    public List<InventoryValueRow> inventoryValue(Long branchId) {
        return jdbc.query("""
                select w.id warehouse_id, w.code warehouse_code, w.name warehouse_name, w.wh_type,
                       coalesce(sum(sl.qty_base), 0) as total_qty,
                       coalesce(sum(sl.qty_base * sl.avg_cost), 0) as total_value
                from warehouse w
                  left join stock_level sl on sl.warehouse_id = w.id
                  left join item i on i.id = sl.item_id and i.deleted = false
                where w.deleted = false""" + bf("w.branch_id", branchId) + """

                group by w.id, w.code, w.name, w.wh_type
                order by w.code
                """,
                (rs, n) -> new InventoryValueRow(rs.getLong("warehouse_id"), rs.getString("warehouse_code"),
                        rs.getString("warehouse_name"), rs.getString("wh_type"),
                        rs.getBigDecimal("total_qty"), rs.getBigDecimal("total_value")));
    }

    public record WarehouseComparisonRow(String warehouseType, long warehouseCount,
                                         long distinctItems, BigDecimal totalQty, BigDecimal totalValue) {}

    /** Depo/vitrin karşılaştırma: depo türüne (SHOWCASE|WAREHOUSE|CONSUMPTION) göre toplamlar. */
    public List<WarehouseComparisonRow> warehouseComparison(Long branchId) {
        return jdbc.query("""
                select w.wh_type,
                       count(distinct w.id) as warehouse_count,
                       count(distinct sl.item_id) as distinct_items,
                       coalesce(sum(sl.qty_base), 0) as total_qty,
                       coalesce(sum(sl.qty_base * sl.avg_cost), 0) as total_value
                from warehouse w
                  left join stock_level sl on sl.warehouse_id = w.id
                where w.deleted = false""" + bf("w.branch_id", branchId) + """

                group by w.wh_type
                order by w.wh_type
                """,
                (rs, n) -> new WarehouseComparisonRow(rs.getString("wh_type"), rs.getLong("warehouse_count"),
                        rs.getLong("distinct_items"), rs.getBigDecimal("total_qty"), rs.getBigDecimal("total_value")));
    }

    private Object[] dateArgs(LocalDate from, LocalDate to) {
        List<Object> args = new java.util.ArrayList<>();
        if (from != null) args.add(Date.valueOf(from));
        if (to != null) args.add(Date.valueOf(to));
        return args.toArray();
    }

    /** Yalnızca dahili, denetlenmiş {@code Long branchId}'den üretilen güvenli SQL parçası. */
    private static String bf(String column, Long branchId) {
        return branchId == null ? "" : " and " + column + " = " + branchId;
    }
}
