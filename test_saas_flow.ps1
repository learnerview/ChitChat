# ChitChat end-to-end API test — matches the CURRENT REST contract.
# Run with the backend up:  .\start_server.ps1   (then in another shell)
#   .\test_saas_flow.ps1
# Covers: auth, invite join links, workspaces, conversations, messages,
# search, read receipts, per-viewer settings, webhooks, deletion.
$BASE = "http://localhost:8080/api"
$stamp = Get-Date -Format "HHmmss"
$pw = "password123"
$script:pass = 0
$script:fail = 0

function Check($name, $cond, $detail = "") {
    if ($cond) { $script:pass++; Write-Host "  [OK]   $name" -ForegroundColor Green }
    else       { $script:fail++; Write-Host "  [FAIL] $name $detail" -ForegroundColor Red }
}

function Api($method, $path, $body, $token, $tenant) {
    $h = @{}
    if ($token)  { $h["Authorization"] = "Bearer $token" }
    if ($tenant) { $h["X-Tenant-Id"] = $tenant }
    $p = @{
        Uri             = "$BASE$path"
        Method          = $method
        UseBasicParsing = $true
        ContentType     = "application/json"
        Headers         = $h
        TimeoutSec      = 15
    }
    if ($null -ne $body) { $p.Body = ($body | ConvertTo-Json -Depth 8) }
    try {
        $r = Invoke-WebRequest @p
        $status = [int]$r.StatusCode; $text = $r.Content
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $status = [int]$resp.StatusCode
            $stream = New-Object IO.StreamReader($resp.GetResponseStream())
            $text = $stream.ReadToEnd()
        } else { $status = 0; $text = $_.Exception.Message }
    }
    $json = $null
    try { $json = $text | ConvertFrom-Json } catch {}
    [pscustomobject]@{ status = $status; json = $json; text = $text }
}

Write-Host "`n=== 1. Auth ===" -ForegroundColor Cyan
$u1 = "owner_$stamp"; $u2 = "member_$stamp"
$r = Api POST "/auth/register" @{ username = $u1; displayName = "Owner $stamp"; password = $pw } $null $null
Check "register owner" ($r.status -in 200,201) "got $($r.status) $($r.text)"
$r = Api POST "/auth/register" @{ username = $u2; displayName = "Member $stamp"; password = $pw } $null $null
Check "register member" ($r.status -in 200,201) "got $($r.status) $($r.text)"

$auth1 = (Api POST "/auth/login" @{ username = $u1; password = $pw } $null $null).json
$auth2 = (Api POST "/auth/login" @{ username = $u2; password = $pw } $null $null).json
Check "login owner"  ($null -ne $auth1.token)
Check "login member" ($null -ne $auth2.token)
$t1 = $auth1.token; $tenant = $auth1.currentTenantId; $t2 = $auth2.token; $u2id = $auth2.userId

Write-Host "`n=== 2. Invite join link ===" -ForegroundColor Cyan
$inv = (Api POST "/invites/generate" @{ tenantId = $tenant; expiresInDays = $null } $t1 $tenant).json
Check "generate invite" ($null -ne $inv.token)
$joinLink = "http://localhost:5173/invite/$($inv.token)"
Write-Host "  join link: $joinLink" -ForegroundColor Yellow
$listed = (Api GET "/invites/$tenant" $null $t1 $tenant).json
Check "invite listed before accept" (@($listed).Count -ge 1)
$acc = Api POST "/invites/accept" @{ token = $inv.token } $t2 $null
Check "member accepts invite" ($acc.status -eq 200 -and $acc.json.tenantId -eq $tenant) "got $($acc.status) $($acc.text)"
# Tokens are minted for one workspace — switch like the app does after joining.
$sw = Api POST "/auth/switch-workspace?tenantId=$tenant" $null $t2 $null
Check "member switches workspace" ($sw.status -eq 200 -and $sw.json.currentTenantId -eq $tenant) "got $($sw.status) $($sw.text)"
$t2 = $sw.json.token
$afterAccept = (Api GET "/invites/$tenant" $null $t1 $tenant).json
Check "single-use invite consumed" (@($afterAccept).Count -eq 0) "got @($($afterAccept.Count))"

Write-Host "`n=== 3. Workspace ===" -ForegroundColor Cyan
$members = (Api GET "/workspaces/$tenant/members" $null $t1 $tenant).json
Check "workspace has 2 members" (@($members).Count -eq 2) "got @($($members.Count))"
$upd = Api PUT "/workspaces/$tenant" @{ name = $auth1.currentTenantName; slug = "ws-$stamp"; description = "e2e test" } $t1 $tenant
Check "update workspace" ($upd.status -eq 200) "got $($upd.status)"

Write-Host "`n=== 4. Conversations ===" -ForegroundColor Cyan
$grp = (Api POST "/conversations/group" @{ name = "E2E Group $stamp"; memberIds = @($u2id) } $t1 $tenant).json
Check "create group with member" ($null -ne $grp.id)
$grpId = $grp.id
Check "group response has viewer settings" ($null -ne $grp.pinned)

$dm = (Api POST "/conversations/dm" @{ userId = $u2id } $t1 $tenant).json
$dm2 = (Api POST "/conversations/dm" @{ userId = $u2id } $t1 $tenant).json
Check "dm unique per pair" ($dm.id -eq $dm2.id)
$dmId = $dm.id

Write-Host "`n=== 5. Messages ===" -ForegroundColor Cyan
$m1 = (Api POST "/conversations/$grpId/messages" @{ content = "hello e2e"; clientMessageId = "c-$stamp-1" } $t1 $tenant).json
$m2 = (Api POST "/conversations/$grpId/messages" @{ content = "searchable zebra"; clientMessageId = "c-$stamp-2" } $t1 $tenant).json
$m3 = (Api POST "/conversations/$grpId/messages" @{ content = "replying now"; clientMessageId = "c-$stamp-3"; replyToId = $m1.id } $t2 $tenant).json
Check "send as member" ($null -ne $m3.id)
Check "reply linked" ($m3.replyToId -eq $m1.id)
$dup = (Api POST "/conversations/$grpId/messages" @{ content = "hello e2e"; clientMessageId = "c-$stamp-1" } $t1 $tenant).json
Check "idempotent resend" ($dup.id -eq $m1.id)

$page = (Api GET "/conversations/$grpId/messages?limit=30" $null $t2 $tenant).json
Check "history visible to member" (@($page.messages).Count -ge 3)

$edited = Api PATCH "/messages/$($m2.id)" @{ content = "searchable zebra EDITED" } $t1 $tenant
Check "edit message" ($edited.status -eq 200 -and $edited.json.edited) "got $($edited.status)"

$s1 = (Api GET "/conversations/$grpId/messages/search?q=zebra" $null $t1 $tenant).json
Check "conversation search" (@($s1).Count -ge 1)
$s2 = (Api GET "/messages/search?q=zebra" $null $t2 $tenant).json
Check "global search" (@($s2).Count -ge 1)

$read = Api POST "/conversations/$grpId/read" @{ sequence = $m2.sequence } $t2 $tenant
Check "mark read" ($read.status -in 200,204) "got $($read.status)"
$convList = (Api GET "/conversations" $null $t2 $tenant).json
$convForRead = $convList | Where-Object { $_.id -eq $grpId }
Check "read state advanced" ($convForRead.lastReadSequence -ge $m2.sequence) "got $($convForRead.lastReadSequence)"

$delMsg = Api DELETE "/messages/$($m3.id)" $null $t2 $tenant
Check "delete own message" ($delMsg.status -eq 204) "got $($delMsg.status)"

Write-Host "`n=== 6. Per-viewer settings ===" -ForegroundColor Cyan
$set = Api PATCH "/conversations/$grpId/settings" @{ pinned = $true; muted = $true; archived = $false; notificationLevel = "MENTIONS" } $t2 $tenant
Check "patch settings" ($set.status -eq 200 -and $set.json.pinned -eq $true -and $set.json.muted -eq $true) "got $($set.status) $($set.text)"
$list2 = Api GET "/conversations" $null $t2 $tenant
$fromList = $list2.json | Where-Object { $_.id -eq $grpId }
Check "settings persist in list" ($fromList.pinned -eq $true -and $fromList.muted -eq $true)
$empty = Api PATCH "/conversations/$grpId/settings" @{} $t2 $tenant
Check "empty patch rejected 400" ($empty.status -eq 400) "got $($empty.status)"

Write-Host "`n=== 7. Permissions ===" -ForegroundColor Cyan
$denied = Api PATCH "/conversations/$grpId/name" @{ name = "hacked" } $t2 $tenant
Check "member cannot rename group" ($denied.status -eq 403) "got $($denied.status)"
$denied2 = Api DELETE "/conversations/$grpId" $null $t2 $tenant
Check "member cannot delete group" ($denied2.status -eq 403) "got $($denied2.status)"

Write-Host "`n=== 8. Webhooks ===" -ForegroundColor Cyan
$hook = (Api POST "/integrations/webhooks" @{ url = "https://example.com/hooks/chitchat"; events = @("message.sent") } $t1 $tenant).json
Check "register webhook" ($null -ne $hook.id)
$hookId = $hook.id
$hupd = Api PUT "/integrations/webhooks/$hookId" @{ url = "https://example.com/hooks/updated"; events = @("message.sent","message.deleted"); active = $false } $t1 $tenant
Check "update webhook" ($hupd.status -eq 200 -and $hupd.json.active -eq $false -and $hupd.json.events.Count -eq 2) "got $($hupd.status) $($hupd.text)"
$hdel = Api DELETE "/integrations/webhooks/$hookId" $null $t1 $tenant
Check "delete webhook" ($hdel.status -eq 204) "got $($hdel.status)"

Write-Host "`n=== 9. Profiles & search ===" -ForegroundColor Cyan
$updProf = Api PUT "/users/profile" @{ displayName = "Member Renamed $stamp" } $t2 $tenant
Check "update display name" ($updProf.status -eq 200 -and $updProf.json.username -eq $u2) "got $($updProf.status) $($updProf.text)"
$found = (Api GET "/users/search?q=$u2" $null $t1 $tenant).json
Check "user search" (@($found).Count -ge 1)
$ticket = Api POST "/auth/ws-ticket" $null $t2 $tenant
Check "ws ticket" ($ticket.status -eq 200 -and $null -ne $ticket.json.ticket) "got $($ticket.status)"

Write-Host "`n=== 10. Deletion ===" -ForegroundColor Cyan
$delDm = Api DELETE "/conversations/$dmId" $null $t1 $tenant
Check "delete dm (either member)" ($delDm.status -eq 204) "got $($delDm.status)"
$gone = Api GET "/conversations/$dmId" $null $t1 $tenant
Check "dm gone after delete" ($gone.status -eq 404) "got $($gone.status)"
$delGrp = Api DELETE "/conversations/$grpId" $null $t1 $tenant
Check "owner deletes group" ($delGrp.status -eq 204) "got $($delGrp.status)"

Write-Host "`n==================================" -ForegroundColor Cyan
Write-Host " PASSED: $script:pass   FAILED: $script:fail" -ForegroundColor $(if ($script:fail -eq 0) { "Green" } else { "Red" })
if ($script:fail -gt 0) { exit 1 }
Write-Host " All flows green. Join link to try in the UI: $joinLink" -ForegroundColor Yellow
exit 0
