Write-Host "Configuration des règles de pare-feu pour FastDrop..." -ForegroundColor Cyan
New-NetFirewallRule -DisplayName "FastDrop File Transfer (TCP 42420)" -Direction Inbound -LocalPort 42420 -Protocol TCP -Action Allow -Profile Private, Domain -ErrorAction SilentlyContinue
New-NetFirewallRule -DisplayName "FastDrop Discovery (UDP 42424)" -Direction Inbound -LocalPort 42424 -Protocol UDP -Action Allow -Profile Private, Domain -ErrorAction SilentlyContinue
Write-Host "✅ Règles FastDrop enregistrées avec succès dans Windows Defender Firewall !" -ForegroundColor Green
