# Start ChitChat Server
$envFile = Get-Content .env
foreach ($line in $envFile) {
    if ($line -match "^[^#].+=.*") {
        $parts = $line.Split('=', 2)
        $key = $parts[0].Trim()
        $value = $parts[1].Trim()
        [System.Environment]::SetEnvironmentVariable($key, $value)
    }
}
mvn spring-boot:run
