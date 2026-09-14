# WinSW <executable> bu betiği çalıştırır (jpackage'ın native servisini değil) —
# DPAPI ile şifrelenmiş anahtarı (wrap-crypto-key-windows.ps1) okuyup uygulamaya
# ortam değişkeni olarak geçirir. Spring Boot'un gevşek bağlama kuralı
# BEAUTYSALONAPP_CRYPTO_KEY_BASE64 → beautysalonapp.crypto.key-base64
# eşlemesini otomatik yapar; ek bir Java değişikliği gerekmez.
#
# Yalnızca WinSW yoluna uygulanabilir (bkz. wrap-crypto-key-windows.ps1 üstündeki
# not); winsw.xml'de <executable> bunu şu şekilde göstermeli:
#   <executable>powershell.exe</executable>
#   <arguments>-ExecutionPolicy Bypass -File "%BASE%\launch-wrapper-windows.ps1"</arguments>
#
# *** DOĞRULANMADI ***: Windows üzerinde test edilmedi.

$ErrorActionPreference = "Stop"

$EncFile = Join-Path $env:ProgramData "BeautySalonApp\config\field-crypto.key.dpapi"
$AppExe  = Join-Path $PSScriptRoot "BeautySalonApp.exe"

if (Test-Path $EncFile) {
    Add-Type -AssemblyName System.Security
    $encBytes = [System.IO.File]::ReadAllBytes($EncFile)
    $plainBytes = [System.Security.Cryptography.ProtectedData]::Unprotect(
        $encBytes, $null, [System.Security.Cryptography.DataProtectionScope]::LocalMachine)
    $env:BEAUTYSALONAPP_CRYPTO_KEY_BASE64 = [System.Text.Encoding]::UTF8.GetString($plainBytes)
}

& $AppExe @args
exit $LASTEXITCODE
