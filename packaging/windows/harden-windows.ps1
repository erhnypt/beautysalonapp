# Servis en az yetkiyle çalışsın + veri dizini ACL sıkılaştırma
# (güvenlik gözden geçirme I1/I2). jpackage --launcher-as-service varsayılan
# olarak servisi LocalSystem hesabıyla kurar. Bu betik KURULUMDAN SONRA,
# Yönetici PowerShell'de BİR KEZ çalıştırılır.
#
# Kullanım:  powershell -ExecutionPolicy Bypass -File harden-windows.ps1
#
# *** DOĞRULANMADI ***: bu depo/oturum Windows üzerinde çalışmıyor; bu betik
# gerçek bir Windows kurulumunda TEST EDİLMEDEN üretime alınmamalıdır. Sözdizimi
# elle gözden geçirildi; `sc.exe`/`icacls` argümanları standart Windows araçlarıdır.

#Requires -RunAsAdministrator

$ErrorActionPreference = "Stop"

$ServiceName    = "BeautySalonApp"
$DataRoot       = Join-Path $env:ProgramData "BeautySalonApp"
# Sanal hesap: parolasız, en az yetkili, yalnızca bu servise özel (Windows 10/Server 2016+).
$ServiceAccount = "NT SERVICE\$ServiceName"

Write-Host "==> [1/2] Servis hesabi: $ServiceAccount"
$svc = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if (-not $svc) {
    Write-Warning "Servis '$ServiceName' bulunamadi - once .msi kurulumunu tamamlayin."
} else {
    # LocalSystem yerine sanal hesap; "type= own" servis bagimsiz calisir.
    sc.exe config $ServiceName obj= $ServiceAccount type= own | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "sc.exe config basarisiz oldu (cikis kodu $LASTEXITCODE)."
    } else {
        Write-Host "    servis artik LocalSystem degil, $ServiceAccount olarak calisacak"
    }
}

Write-Host "==> [2/2] Veri dizini ACL: $DataRoot"
if (Test-Path $DataRoot) {
    icacls $DataRoot /inheritance:r | Out-Null
    icacls $DataRoot /grant:r "${ServiceAccount}:(OI)(CI)F" | Out-Null
    icacls $DataRoot /grant:r "BUILTIN\Administrators:(OI)(CI)F" | Out-Null
    icacls $DataRoot /grant:r "NT AUTHORITY\SYSTEM:(OI)(CI)F" | Out-Null
    Write-Host "    yalnizca $ServiceAccount + Administrators + SYSTEM erisebilir"
} else {
    Write-Warning "$DataRoot bulunamadi - once .msi kurulumunu tamamlayin."
}

if ($svc) {
    Restart-Service -Name $ServiceName -ErrorAction SilentlyContinue
}
Write-Host "==> Tamam."
