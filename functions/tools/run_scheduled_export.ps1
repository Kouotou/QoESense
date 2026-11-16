# Scheduled free-tier export (Windows Task Scheduler, "QoESense daily export").
# 1. Runs export_firestore.js: incremental and read-only, unless -DeleteExported is passed.
# 2. Saves a dated zip snapshot of the export folder, keeping the newest -KeepBackups.
# 3. Writes a log per run.
# It never stops on a failed export: the snapshot of the existing files is still taken. The
# exit code tells Task Scheduler whether the export worked, so the "Last Run Result" column
# shows failures.
#
# Manual run:
#   powershell -ExecutionPolicy Bypass -File run_scheduled_export.ps1
param(
  [string]$ExportDir = (Join-Path $env:USERPROFILE 'QoESense-exports'),
  [string]$BackupDir = (Join-Path $env:USERPROFILE 'QoESense-backups\exports'),
  [string]$LogDir = (Join-Path $env:USERPROFILE 'QoESense-backups\export-logs'),
  # The test devices (friend's iPhone, Infinix test phone): their data is test data.
  [string]$Exclude = 'UD1DfRM9K9Wow7GaL8BrGfbSFiG2,0y5ZtaFlJbMVBMyigwzN6TrKEHq1',
  [string]$Node = 'node',
  [int]$KeepBackups = 30,
  [int]$KeepLogs = 90,
  # Option A: delete exported batch documents from Firestore. Off until the study needs it
  # (FREE_TIER_MIGRATION.md section 2). After that, the snapshots are the only other copy.
  [switch]$DeleteExported
)
$ErrorActionPreference = 'Stop'
$stamp = Get-Date -Format 'yyyy-MM-dd_HHmm'
New-Item -ItemType Directory -Force $BackupDir, $LogDir, $ExportDir | Out-Null
$log = Join-Path $LogDir "export_$stamp.log"
function Log($msg) { "$(Get-Date -Format 's')  $msg" | Out-File -FilePath $log -Append -Encoding utf8 }

$exporter = Join-Path $PSScriptRoot 'export_firestore.js'
$exportArgs = @($exporter, '--out', $ExportDir, '--exclude', $Exclude)
if ($DeleteExported) { $exportArgs += '--delete-exported' }

Log "export start: $Node $($exportArgs -join ' ')"
# cmd /c keeps the exporter's stderr out of PowerShell's error stream (Windows PowerShell 5.1
# would turn it into a terminating error), and both streams go to the log.
$quoted = ($exportArgs | ForEach-Object { '"' + $_ + '"' }) -join ' '
cmd /c "`"$Node`" $quoted >> `"$log`" 2>&1"
$exportCode = $LASTEXITCODE
Log "export exit code: $exportCode"

# Snapshot of the whole export folder (NDJSON, CSV, checkpoint).
try {
  $zip = Join-Path $BackupDir "QoESense-exports_$stamp.zip"
  Compress-Archive -Path (Join-Path $ExportDir '*') -DestinationPath $zip -CompressionLevel Optimal -Force
  Log ("snapshot: $zip ({0:N0} bytes)" -f (Get-Item $zip).Length)
  Get-ChildItem $BackupDir -Filter 'QoESense-exports_*.zip' | Sort-Object Name -Descending |
    Select-Object -Skip $KeepBackups | ForEach-Object { Log "pruned old snapshot $($_.Name)"; Remove-Item $_.FullName -Confirm:$false }
  $backupCode = 0
} catch {
  Log "snapshot FAILED: $_"
  $backupCode = 2
}
Get-ChildItem $LogDir -Filter 'export_*.log' | Sort-Object Name -Descending |
  Select-Object -Skip $KeepLogs | Remove-Item -Confirm:$false

if ($exportCode -ne 0) { exit 1 }
exit $backupCode
