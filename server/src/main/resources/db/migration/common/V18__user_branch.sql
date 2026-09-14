-- Güvenlik gözden geçirme B5: kullanıcı-şube ataması. Boş atama (satır yok) = kısıtlama
-- yok — v1 tek-şube ve mevcut çok şubeli kurulumların davranışı BİREBİR AYNI kalır.
-- Bir kullanıcıya en az bir satır eklenirse yalnızca o şube(ler)e kısıtlanır
-- (bkz. AppUser.assignedBranchIds, BranchContextFilter, UserService.resolveRequestedBranch).
CREATE TABLE user_branch (
    user_id   BIGINT NOT NULL REFERENCES app_user(id),
    branch_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, branch_id)
);
