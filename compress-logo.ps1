# 压缩 logo.png 到 256x256
Add-Type -AssemblyName System.Drawing

$srcPath = "qianduan/admin-v3/public/logo.png"
$dstPath = "qianduan/admin-v3/public/logo.png"

$img = [System.Drawing.Image]::FromFile($srcPath)

# 按比例缩放到 256 宽度
$newWidth = 256
$newHeight = [int]($img.Height * ($newWidth / $img.Width))

$bmp = New-Object System.Drawing.Bitmap($newWidth, $newHeight)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.DrawImage($img, 0, 0, $newWidth, $newHeight)
$g.Dispose()
$img.Dispose()

$bmp.Save($dstPath, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

Write-Host "Done! Resized to ${newWidth}x${newHeight}"
