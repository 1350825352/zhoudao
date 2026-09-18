# 复制核心代码到 zhoudao2
$src = "H:\traeproject\zhoudao"
$dst = "H:\traeproject\zhoudao2"

# 创建目标目录
if (-not (Test-Path $dst)) { New-Item -ItemType Directory -Path $dst -Force }

# 1. 核心目录（递归复制）
$coreDirs = @("src", "sql", "deploy-package")
foreach ($dir in $coreDirs) {
    $s = Join-Path $src $dir
    $d = Join-Path $dst $dir
    if (Test-Path $s) {
        Write-Host "复制 $dir ..."
        Copy-Item $s $d -Recurse -Force
    }
}

# 2. 前端目录（排除 node_modules 和 dist）
$qianduanSrc = Join-Path $src "qianduan"
$qianduanDst = Join-Path $dst "qianduan"
New-Item -ItemType Directory -Path $qianduanDst -Force | Out-Null
foreach ($sub in @("admin-v3", "screen-v3", "h5-v3")) {
    $s = Join-Path $qianduanSrc $sub
    $d = Join-Path $qianduanDst $sub
    if (Test-Path $s) {
        Write-Host "复制 qianduan\$sub (排除 node_modules/dist) ..."
        New-Item -ItemType Directory -Path $d -Force | Out-Null
        # 用 robocopy 排除 node_modules 和 dist
        robocopy $s $d /E /XD node_modules dist /NJH /NJS /NFL /NDL /NP
    }
}

# 3. 核心配置文件
$coreFiles = @("pom.xml", ".gitignore", "README.md")
foreach ($f in $coreFiles) {
    $s = Join-Path $src $f
    if (Test-Path $s) {
        Copy-Item $s $dst -Force
        Write-Host "复制 $f"
    }
}

# 4. 文档文件
$docs = Get-ChildItem $src -File | Where-Object {
    $_.Extension -in @(".txt", ".doc", ".docx", ".md") -and
    $_.Name -ne "commit-msg.txt"
}
foreach ($doc in $docs) {
    Copy-Item $doc.FullName $dst -Force
    Write-Host "复制 $($doc.Name)"
}

Write-Host "`n完成！目标目录: $dst"
Write-Host "总文件数:"
(Get-ChildItem $dst -Recurse -File).Count
