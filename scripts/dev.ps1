param([ValidateSet('backend','frontend','database','build')] [string]$Task = 'backend')
$ErrorActionPreference = 'Stop'
$ProjectDir = Split-Path $PSScriptRoot -Parent
switch ($Task) {
  'database' { Set-Location $ProjectDir; docker compose up -d mysql }
  'backend' { Set-Location "$ProjectDir/backend"; mvn spring-boot:run }
  'frontend' { Set-Location "$ProjectDir/frontend"; npm run dev }
  'build' { Set-Location "$ProjectDir/frontend"; npm ci; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; npm run build; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; Set-Location "$ProjectDir/backend"; mvn clean package }
}
exit $LASTEXITCODE
