// Kullanıcıya görünen tüm metin buradan gelir (CLAUDE.md #6).
// Ürün tek dilli (TR) başlıyor; ileride anahtar tabanlı çoklu dil eklenebilir.

export const t = {
  appName: "BeautySalonApp",
  tagline: "Güzellik Merkezi Yönetim Yazılımı",

  nav: {
    dashboard: "Günlük Analiz",
    appointments: "Randevular",
    parties: "Cari Hesaplar",
    stock: "Stok",
    finance: "Kasa & Finans",
    bankReconciliation: "Banka Mutabakatı",
    invoices: "Faturalar",
    contracts: "Sözleşmeler",
    staff: "Personel",
    loyalty: "Sadakat",
    reports: "Raporlar",
    notifications: "Bildirimler",
    settings: "Ayarlar",
    users: "Kullanıcılar",
    audit: "İşlem Kayıtları",
    license: "Lisans",
    backup: "Yedekleme",
    branches: "Şubeler",
    support: "Destek",
  },

  auth: {
    loginTitle: "Giriş Yap",
    username: "Kullanıcı adı",
    password: "Parola",
    signIn: "Giriş",
    signOut: "Çıkış",
    badCredentials: "Kullanıcı adı veya parola hatalı",
    accountLocked: "Hesap geçici olarak kilitlendi. Lütfen daha sonra tekrar deneyin.",
    mustChangePassword: "Devam etmek için parolanızı değiştirmelisiniz.",
    currentPassword: "Mevcut parola",
    newPassword: "Yeni parola",
    changePassword: "Parolayı Değiştir",
    passwordChanged: "Parola güncellendi.",
    pinSetupTitle: "Güvenlik PIN'i Belirleyin",
    pinSetupInfo:
      "Rolünüz kritik yetkiler içerdiğinden ek bir güvenlik adımı olarak 4-6 haneli bir PIN belirlemeniz gerekiyor. Bu PIN'i her girişte parolanızla birlikte kullanacaksınız.",
    pinVerifyTitle: "PIN Doğrulama",
    pinVerifyInfo: "Devam etmek için PIN'inizi girin.",
    pin: "PIN",
    pinConfirm: "PIN (tekrar)",
    pinMismatch: "Girdiğiniz PIN'ler eşleşmiyor.",
    pinInvalidFormat: "PIN 4-6 haneli rakamdan oluşmalıdır.",
    pinSetupSubmit: "PIN'i Kaydet",
    pinVerifySubmit: "Doğrula",
    pinWrong: "PIN hatalı.",
    pinLocked: "Çok sayıda hatalı deneme nedeniyle oturum sonlandırıldı. Lütfen tekrar giriş yapın.",
    pinLockedTitle: "PIN Geçici Olarak Kilitli",
    pinLockedUntil: (when: string) => `Çok sayıda hatalı deneme nedeniyle PIN kilitlendi. ${when} itibarıyla tekrar deneyebilirsiniz.`,
  },

  license: {
    ACTIVE: "Lisans etkin",
    EXPIRING: "Lisans yakında bitiyor",
    GRACE: "Ödemesiz kullanım süresi (grace)",
    READ_ONLY: "Salt okunur — yeni işlem yapılamaz",
    LOCKED: "Kilitli — yalnızca veri dışa aktarma",
    TAMPERED: "Sistem saati tutarsızlığı — çevrimiçi doğrulama gerekli",
    devMode: "Geliştirme modu — tüm modüller açık",
    uploadTitle: "Lisans dosyası yükle (.lic)",
    heartbeatNow: "Şimdi doğrula",
    daysRemaining: "kalan gün",
  },

  common: {
    save: "Kaydet",
    cancel: "İptal",
    add: "Ekle",
    edit: "Düzenle",
    loading: "Yükleniyor…",
    error: "Bir hata oluştu",
    noRecords: "Kayıt yok",
    search: "Ara",
    confirm: "Onayla",
    yes: "Evet",
    no: "Hayır",
    actions: "İşlemler",
    enabled: "Etkin",
    disabled: "Pasif",
  },

  users: {
    title: "Kullanıcılar",
    newUser: "Yeni Kullanıcı",
    fullName: "Ad Soyad",
    roles: "Roller",
    lastLogin: "Son giriş",
    resetPassword: "Parola Sıfırla",
    resetPin: "PIN Sıfırla",
    confirmPinReset: "PIN sıfırlansın mı? Kullanıcı bir sonraki girişte yeni bir PIN belirlemek zorunda kalacak.",
    pinReset: "PIN sıfırlandı.",
    editBranches: "Şubeleri Düzenle",
    branchRestriction: "Şube Kısıtlaması",
    branchRestrictionNone: "Kısıtlama yok (tüm şubelerde çalışabilir)",
    branchRestrictionHelp:
      "İşaretli şube(ler) dışında hiçbir şubede işlem yapamaz ve o şubeleri göremez.",
  },

  settings: {
    title: "Ayarlar",
    key: "Anahtar",
    value: "Değer",
    description: "Açıklama",
    secretHidden: "•••• (gizli)",
  },

  audit: {
    title: "İşlem Kayıtları",
    time: "Zaman",
    actor: "Kullanıcı",
    action: "İşlem",
    entity: "Kayıt",
    summary: "Özet",
  },

  dashboard: {
    title: "Bugün",
    comingSoon: "Bu modül geliştirme aşamasında.",
    noReportingAccess:
      "Günlük mali özeti görüntülemek için raporlama yetkiniz bulunmuyor. Yetki için yöneticinize başvurun.",
  },
  finance: {
    fetchLiveRate: "Canlı kur",
    fetchingLiveRate: "Alınıyor…",
  },
} as const;

export const roleLabels: Record<string, string> = {
  ADMIN: "Yönetici",
  MUDUR: "Müdür",
  KASIYER: "Kasiyer",
  PERSONEL: "Personel",
  RAPOR_OKUYUCU: "Rapor Okuyucu",
};

/** Yetki = modül × işlem (bkz. server: security.domain.Permission). */
export const permissionGroups: { module: string; permissions: { code: string; label: string }[] }[] = [
  {
    module: "Çekirdek Yönetim",
    permissions: [
      { code: "SETTINGS_VIEW", label: "Ayarları Görüntüleme" },
      { code: "SETTINGS_EDIT", label: "Ayarları Düzenleme" },
      { code: "USER_VIEW", label: "Kullanıcıları Görüntüleme" },
      { code: "USER_EDIT", label: "Kullanıcıları Düzenleme" },
      { code: "AUDIT_VIEW", label: "İşlem Kayıtlarını Görüntüleme" },
      { code: "LICENSE_MANAGE", label: "Lisans Yönetimi" },
      { code: "BACKUP_RUN", label: "Yedek Alma" },
      { code: "BACKUP_RESTORE", label: "Yedekten Geri Yükleme" },
      { code: "DATA_EXPORT", label: "Veri Dışa Aktarma" },
    ],
  },
  {
    module: "Stok",
    permissions: [
      { code: "STOCK_VIEW", label: "Görüntüleme" },
      { code: "STOCK_ADD", label: "Ekleme" },
      { code: "STOCK_EDIT", label: "Düzenleme" },
      { code: "STOCK_DELETE", label: "Silme" },
      { code: "STOCK_REPORT", label: "Raporlar" },
    ],
  },
  {
    module: "Personel",
    permissions: [
      { code: "STAFF_VIEW", label: "Görüntüleme" },
      { code: "STAFF_ADD", label: "Ekleme" },
      { code: "STAFF_EDIT", label: "Düzenleme" },
      { code: "STAFF_DELETE", label: "Silme" },
      { code: "STAFF_REPORT", label: "Raporlar" },
    ],
  },
  {
    module: "Cari Hesaplar",
    permissions: [
      { code: "PARTY_VIEW", label: "Görüntüleme" },
      { code: "PARTY_ADD", label: "Ekleme" },
      { code: "PARTY_EDIT", label: "Düzenleme" },
      { code: "PARTY_DELETE", label: "Silme" },
      { code: "PARTY_REPORT", label: "Raporlar" },
    ],
  },
  {
    module: "Kasa & Finans",
    permissions: [
      { code: "FINANCE_VIEW", label: "Görüntüleme" },
      { code: "FINANCE_ADD", label: "Ekleme" },
      { code: "FINANCE_EDIT", label: "Düzenleme" },
      { code: "FINANCE_VOID", label: "Ters Kayıt/İptal" },
      { code: "FINANCE_REPORT", label: "Raporlar" },
    ],
  },
  {
    module: "Faturalar",
    permissions: [
      { code: "INVOICE_VIEW", label: "Görüntüleme" },
      { code: "INVOICE_ADD", label: "Ekleme" },
      { code: "INVOICE_EDIT", label: "Düzenleme" },
      { code: "INVOICE_VOID", label: "Ters Kayıt/İptal" },
      { code: "INVOICE_REPORT", label: "Raporlar" },
    ],
  },
  {
    module: "Randevular",
    permissions: [
      { code: "APPOINTMENT_VIEW", label: "Görüntüleme" },
      { code: "APPOINTMENT_ADD", label: "Ekleme" },
      { code: "APPOINTMENT_EDIT", label: "Düzenleme" },
      { code: "APPOINTMENT_CANCEL", label: "İptal" },
      { code: "APPOINTMENT_REPORT", label: "Raporlar" },
    ],
  },
  {
    module: "Sözleşmeler",
    permissions: [
      { code: "CONTRACT_VIEW", label: "Görüntüleme" },
      { code: "CONTRACT_ADD", label: "Ekleme" },
      { code: "CONTRACT_EDIT", label: "Düzenleme" },
      { code: "CONTRACT_VOID", label: "Ters Kayıt/İptal" },
      { code: "CONTRACT_REPORT", label: "Raporlar" },
    ],
  },
  {
    module: "Sadakat",
    permissions: [
      { code: "LOYALTY_VIEW", label: "Görüntüleme" },
      { code: "LOYALTY_ADD", label: "Ekleme" },
      { code: "LOYALTY_EDIT", label: "Düzenleme" },
      { code: "LOYALTY_REPORT", label: "Raporlar" },
    ],
  },
  {
    module: "Bildirimler",
    permissions: [
      { code: "NOTIFICATION_VIEW", label: "Görüntüleme" },
      { code: "NOTIFICATION_SEND", label: "Gönderme" },
      { code: "NOTIFICATION_TEMPLATE_EDIT", label: "Şablon Düzenleme" },
    ],
  },
  {
    module: "Raporlama Merkezi",
    permissions: [{ code: "REPORTING_VIEW", label: "Görüntüleme" }],
  },
];
