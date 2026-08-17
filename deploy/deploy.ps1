param(
    [Parameter(Mandatory=$true)]
    [string]$Company
)

$BASE_DIR = Split-Path -Parent $PSScriptRoot
$CONFIG_DIR = Join-Path $BASE_DIR "deploy\configs\$Company"
$BACKEND_RESOURCE_DIR = Join-Path $BASE_DIR "src\main\resources"
$FRONTEND_DIRS = @(
    Join-Path $BASE_DIR "qianduan\admin-v3"
    Join-Path $BASE_DIR "qianduan\screen-v3"
    Join-Path $BASE_DIR "qianduan\h5-v3"
)

Write-Host "Deploy script - Company: $Company"

if (-not (Test-Path $CONFIG_DIR)) {
    Write-Host "Error: Config directory not found - $CONFIG_DIR"
    exit 1
}

$backendConfig = Join-Path $CONFIG_DIR "backend.yml"
$frontendConfig = Join-Path $CONFIG_DIR "frontend.env"

if (-not (Test-Path $backendConfig)) {
    Write-Host "Error: Backend config not found - $backendConfig"
    exit 1
}

if (-not (Test-Path $frontendConfig)) {
    Write-Host "Error: Frontend config not found - $frontendConfig"
    exit 1
}

Write-Host "1. Configuring backend..."
$targetBackendConfig = Join-Path $BACKEND_RESOURCE_DIR "application-$Company.yml"
Copy-Item -Path $backendConfig -Destination $targetBackendConfig -Force
Write-Host "Copied backend config: $targetBackendConfig"

Write-Host "2. Configuring frontend..."
foreach ($frontendDir in $FRONTEND_DIRS) {
    if (Test-Path $frontendDir) {
        $targetEnvFile = Join-Path $frontendDir ".env"
        Copy-Item -Path $frontendConfig -Destination $targetEnvFile -Force
        Write-Host "Copied frontend config: $targetEnvFile"
    } else {
        Write-Host "Skipping missing directory: $frontendDir"
    }
}

Write-Host "Deployment complete!"
Write-Host "Start backend: mvn spring-boot:run -Dspring-boot.run.profiles=base,$Company"
Write-Host "Start frontend: cd qianduan/admin-v3; npm run dev"
