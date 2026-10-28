param([string]$BaseUrl = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'
$tokenResponse = Invoke-RestMethod -Method Post -Uri 'http://localhost:8088/realms/aegis/protocol/openid-connect/token' -Body @{grant_type='password';client_id='aegis-dev-cli';username='aegis-dev';password='dev123';scope='notification:read'}
$authHeaders = @{Authorization="Bearer $($tokenResponse.access_token)"}
$results = [System.Collections.Generic.List[object]]::new()
function Check([string]$Name, [bool]$Condition) {
  $results.Add([pscustomobject]@{test=$Name;passed=$Condition})
  if (-not $Condition) { throw "FAILED: $Name" }
}
function Query([string]$Parameters) {
  Invoke-RestMethod "$BaseUrl/api/v1/notifications?$Parameters" -Headers $authHeaders
}
$first = Query 'page=0&size=10&search=issue84-manual-fixture'
$second = Query 'page=1&size=10&search=issue84-manual-fixture'
$third = Query 'page=2&size=10&search=issue84-manual-fixture'
Check 'Total real: 24 fixtures' ($first.totalElements -eq 24)
Check 'Paginas: 10 + 10 + 4' ($first.content.Count -eq 10 -and $second.content.Count -eq 10 -and $third.content.Count -eq 4)
$ids = @($first.content.id) + @($second.content.id) + @($third.content.id)
Check 'Sin IDs repetidos entre paginas' (($ids | Select-Object -Unique).Count -eq 24)
$filtered = Query 'page=0&search=issue84&channel=EMAIL&priority=MEDIUM&status=SENT_VIA_FALLBACK'
Check 'Filtros combinados: 1 resultado esperado' ($filtered.totalElements -eq 1 -and $filtered.content[0].recipient -eq 'issue84-04@example.com')
Check 'Proveedor incluido' ($filtered.content[0].providerUsed -eq 'Local fixture provider')
$idResult = Query "page=0&search=$($filtered.content[0].id)"
Check 'Busqueda por UUID' ($idResult.totalElements -eq 1)
$recipient = Query 'page=0&recipient=ISSUE84-04'
Check 'Destinatario ignora mayusculas' ($recipient.totalElements -eq 1)
$dates = Query 'page=0&size=100&search=issue84&from=2026-10-14T06:00:00Z&to=2026-10-15T06:00:00Z'
Check 'Rango de un dia local: 12 resultados' ($dates.totalElements -eq 12)
$empty = Query 'page=0&search=issue84-no-such-notification'
Check 'Cero resultados reales' ($empty.totalElements -eq 0 -and $empty.content.Count -eq 0)
$literal = Query 'page=0&search=%25'
Check 'Porcentaje se interpreta literalmente' ($literal.totalElements -eq 0)
foreach ($invalid in @('page=-1','page=0&size=101','page=0&channel=INVALID','page=0&from=2026-10-15T00:00:00Z&to=2026-10-14T00:00:00Z')) {
  $response = Invoke-WebRequest "$BaseUrl/api/v1/notifications?$invalid" -Headers $authHeaders -SkipHttpErrorCheck
  Check "HTTP 400: $invalid" ($response.StatusCode -eq 400)
}
$unauth = Invoke-WebRequest "$BaseUrl/api/v1/notifications?page=0" -SkipHttpErrorCheck
Check 'Sin token: HTTP 401' ($unauth.StatusCode -eq 401)
$legacy = Query 'limit=6'
Check 'Dashboard conserva contrato array' ($legacy -is [array] -and $legacy.Count -le 6)
@{executedAt=(Get-Date -Format o);baseUrl=$BaseUrl;results=$results;firstPage=$first;combinedFilters=$filtered;dateRange=$dates;empty=$empty} | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 "$PSScriptRoot/evidence/api-checks.json"
$results | Format-Table -AutoSize
