# Alan şifreleme anahtarını (CryptoKeyProvider, güvenlik gözden geçirme F3) Windows
# DPAPI (LocalMachine kapsamı) ile şifreler ve düz metin dosyasını siler.
#
# *** ÖNEMLİ SINIRLAMA ***: jpackage'ın varsayılan `--launcher-as-service` yolu
# JVM'i doğrudan native bir servis olarak başlatır; başlangıçta dinamik bir
# "önce çöz, sonra başlat" adımı ekleyemez. Bu koruma seviyesi yalnızca WinSW
# yoluna (packaging/windows/winsw.xml, bu depoda zaten belgelenmiş "daha fazla
# kontrol" alternatifi) geçildiğinde işe yarar — bkz. launch-wrapper-windows.ps1.
# WinSW'ye geçilmezse harden-windows.ps1'in ACL sıkılaştırması tek koruma katmanı
# olarak kalır (dosya yine düz metin ama yalnızca servis hesabı/Administrators okur).
#
# Kullanım (Yönetici PowerShell, kurulumdan ve anahtar üretiminden sonra, bir kez):
#   powershell -ExecutionPolicy Bypass -File wrap-crypto-key-windows.ps1
#
# *** DOĞRULANMADI ***: bu oturum Windows üzerinde çalışmıyor; gerçek bir Windows
# kurulumunda test edilmeden üretime alınmamalıdır.

#Requires -RunAsAdministrator
$ErrorActionPreference = "Stop"

$KeyFile = Join-Path $env:ProgramData "BeautySalonApp\config\field-crypto.key"
$EncFile = "$KeyFile.dpapi"

if (-not (Test-Path $KeyFile)) {
    throw "$KeyFile yok. Once uygulamayi bir kez calistirip anahtarin uretilmesini bekleyin."
}
if (Test-Path $EncFile) {
    throw "$EncFile zaten var. Once elle silin (yeniden sarmalamak icin)."
}

Add-Type -AssemblyName System.Security

Write-Host "==> [1/2] Anahtar DPAPI (LocalMachine) ile sifreleniyor"
$plainText = (Get-Content $KeyFile -Raw).Trim()
$plainBytes = [System.Text.Encoding]::UTF8.GetBytes($plainText)
$encBytes = [System.Security.Cryptography.ProtectedData]::Protect(
    $plainBytes, $null, [System.Security.Cryptography.DataProtectionScope]::LocalMachine)
[System.IO.File]::WriteAllBytes($EncFile, $encBytes)

# Round-trip dogrulamasi.
$roundTrip = [System.Security.Cryptography.ProtectedData]::Unprotect(
    $encBytes, $null, [System.Security.Cryptography.DataProtectionScope]::LocalMachine)
if ([System.Text.Encoding]::UTF8.GetString($roundTrip) -ne $plainText) {
    Remove-Item $EncFile -Force
    throw "Round-trip dogrulamasi basarisiz - duz metin dosya SILINMEDI."
}

Write-Host "==> [2/2] Duz metin anahtar dosyasi guvenli sekilde siliniyor"
$randomBytes = New-Object byte[] (Get-Item $KeyFile).Length
(New-Object System.Random).NextBytes($randomBytes)
[System.IO.File]::WriteAllBytes($KeyFile, $randomBytes)
Remove-Item $KeyFile -Force

Write-Host "==> Tamam: $EncFile (DPAPI, LocalMachine kapsami)."
Write-Host "    WinSW yoluna geciyorsaniz launch-wrapper-windows.ps1'i executable olarak ayarlayin."
