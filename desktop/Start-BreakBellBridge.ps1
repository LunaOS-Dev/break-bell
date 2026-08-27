$node = Get-Command node -ErrorAction Stop
$scriptPath = Join-Path $PSScriptRoot 'break-bell-bridge.mjs'

& $node.Source $scriptPath
