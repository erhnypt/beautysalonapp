# Sessiz / uzaktan kurulum (§5.4). Yönetici PowerShell'de çalıştırın.
#
#   .\silent-install.ps1 -Msi .\BeautySalonApp-1.0.0-windows-x64.msi `
#        -LicenseKey BSA-XXXX-XXXX-XXXX-XXXX -InstallMode SINGLE `
#        -BackupDir 'D:\Yedek\BeautySalonApp'
param(
  [Parameter(Mandatory=$true)][string]$Msi,
  [string]$LicenseKey = "",
  [ValidateSet("SINGLE","MULTI")][string]$InstallMode = "SINGLE",
  [string]$BackupDir = "",
  [switch]$LanAccess,
  [string]$LogFile = "$env:TEMP\beautysalonapp-install.log"
)

$props = @("INSTALL_MODE=$InstallMode")
if ($LicenseKey) { $props += "LICENSE_KEY=$LicenseKey" }
if ($BackupDir)  { $props += "BACKUP_DIR=`"$BackupDir`"" }
if ($LanAccess)  { $props += "LAN_ACCESS=1" }

$argList = @("/i", "`"$Msi`"", "/qn", "/l*v", "`"$LogFile`"") + $props
Write-Host "msiexec $($argList -join ' ')"
$p = Start-Process msiexec.exe -ArgumentList $argList -Wait -PassThru
if ($p.ExitCode -ne 0) {
  Write-Error "Kurulum başarısız (ExitCode $($p.ExitCode)). Log: $LogFile"
  exit $p.ExitCode
}

# Servisin ayağa kalkmasını bekle
for ($i=0; $i -lt 30; $i++) {
  try { Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:8734/actuator/health" -TimeoutSec 2 | Out-Null; break }
  catch { Start-Sleep 2 }
}
Write-Host "Kurulum tamam. Arayüz: http://localhost:8734"
