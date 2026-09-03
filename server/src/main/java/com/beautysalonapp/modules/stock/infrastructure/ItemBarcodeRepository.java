package com.beautysalonapp.modules.stock.infrastructure;

import com.beautysalonapp.modules.stock.domain.ItemBarcode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemBarcodeRepository extends JpaRepository<ItemBarcode, Long> {
    Optional<ItemBarcode> findByBranchIdAndBarcode(Long branchId, String barcode);
    boolean existsByBranchIdAndBarcode(Long branchId, String barcode);
    List<ItemBarcode> findAllByItemIdAndDeletedFalse(Long itemId);
}
