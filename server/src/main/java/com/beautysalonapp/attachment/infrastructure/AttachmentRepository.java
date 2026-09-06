package com.beautysalonapp.attachment.infrastructure;

import com.beautysalonapp.attachment.domain.Attachment;
import com.beautysalonapp.attachment.domain.AttachmentOwnerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findAllByOwnerTypeAndOwnerIdAndDeletedFalseOrderByIdDesc(String ownerType, Long ownerId);

    default List<Attachment> findAllFor(AttachmentOwnerType ownerType, long ownerId) {
        return findAllByOwnerTypeAndOwnerIdAndDeletedFalseOrderByIdDesc(ownerType.name(), ownerId);
    }
}
