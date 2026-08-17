# Check logo.png size and give suggestions
$logoPath = "qianduan/admin-v3/public/logo.png"

if (-not (Test-Path $logoPath)) {
    Write-Host "[ERROR] File not found: $logoPath" -ForegroundColor Red
    exit
}

$file = Get-Item $logoPath
$sizeKB = [math]::Round($file.Length / 1KB, 2)
$sizeMB = [math]::Round($file.Length / 1MB, 2)

Write-Host ""
Write-Host "=== Logo File Check ===" -ForegroundColor Cyan
Write-Host "File: $($file.FullName)"
Write-Host "Size: $sizeKB KB ($sizeMB MB)"
Write-Host ""

Add-Type -AssemblyName System.Drawing
try {
    $img = [System.Drawing.Image]::FromFile($file.FullName)
    Write-Host "Dimensions: $($img.Width) x $($img.Height) pixels"
    $img.Dispose()
} catch {
    Write-Host "Dimensions: cannot read" -ForegroundColor Yellow
}

Write-Host ""

if ($file.Length -gt 500KB) {
    Write-Host "[WARN] File too large, please compress!" -ForegroundColor Yellow
    Write-Host "   Recommended steps:"
    Write-Host "   1. Open with Paint -> Resize -> 256x256 pixels"
    Write-Host "   2. Save as PNG"
    Write-Host "   Target size: less than 100KB"
} elseif ($file.Length -gt 100KB) {
    Write-Host "[OK] File size acceptable ($sizeKB KB)" -ForegroundColor Green
    Write-Host "   Can optimize to less than 100KB if needed"
} else {
    Write-Host "[OK] File size perfect ($sizeKB KB)!" -ForegroundColor Green
}

Write-Host ""
