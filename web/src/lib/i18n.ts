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
    invoices: "Faturalar",
    contracts: "Sözleşmeler",
    staff: "Personel",
    loyalty: "Sadakat",
    reports: "Raporlar",
    settings: "Ayarlar",
    users: "Kullanıcılar",
    audit: "İşlem Kayıtları",
    license: "Lisans",
    backup: "Yedekleme",
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
  },
} as const;

export const roleLabels: Record<string, string> = {
  ADMIN: "Yönetici",
  MUDUR: "Müdür",
  KASIYER: "Kasiyer",
  PERSONEL: "Personel",
  RAPOR_OKUYUCU: "Rapor Okuyucu",
};
