#Requires -Version 7.0
param(
    [Parameter(Mandatory = $true)]
    [string]$Token,
    [string]$BaseUrl = "http://127.0.0.1:8090",
    [long]$PlanId = 63,
    [int]$ScanLimit = 120
)

$ErrorActionPreference = "Stop"
$headers = @{ Authorization = "Bearer $Token" }

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        $Body = $null
    )
    $uri = "$BaseUrl$Path"
    if ($null -eq $Body) {
        return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers -TimeoutSec 30
    }
    return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 8) -TimeoutSec 30
}

$createdUnavailableId = $null
try {
    $items = (Invoke-Api -Method "GET" -Path "/api/v3/schedule-plans/$PlanId/items").data
    if (-not $items -or $items.Count -eq 0) {
        throw "No plan items found in plan $PlanId"
    }

    $scanItems = $items | Select-Object -First $ScanLimit
    $target = $null
    foreach ($it in $scanItems) {
        $candBody = @{ planItemId = $it.id; includeUnavailable = $false; limit = 40 }
        $cand = (Invoke-Api -Method "POST" -Path "/api/v5/candidate-positions/generate" -Body $candBody).data
        if (-not $cand.candidates -or $cand.candidates.Count -eq 0) { continue }

        $srcRoom = $cand.sourceClassroomId
        $srcW = $cand.sourceWeekday
        $srcS = $cand.sourceStartPeriod
        $keepRoomCount = ($cand.candidates | Where-Object { $_.classroomId -eq $srcRoom -and ($_.weekday -ne $srcW -or $_.startPeriod -ne $srcS) }).Count
        if ($keepRoomCount -gt 0) {
            $target = [PSCustomObject]@{
                id = $it.id
                teacherId = $it.teacherId
                weekday = $it.weekday
                startPeriod = $it.startPeriod
                roomId = $it.classroomId
                keepRoomCount = $keepRoomCount
            }
            break
        }
    }

    if ($null -eq $target) {
        throw "No suitable plan item found for KEEP_ROOM_CHANGE_TIME in first $ScanLimit items."
    }

    $timeSlots = (Invoke-Api -Method "GET" -Path "/api/time-slots").data
    $periodNo = [int](($target.startPeriod + 1) / 2)
    $slot = $timeSlots | Where-Object { $_.dayOfWeek -eq $target.weekday -and $_.periodNo -eq $periodNo } | Select-Object -First 1
    if ($null -eq $slot) {
        throw "Time slot not found for weekday=$($target.weekday), periodNo=$periodNo"
    }

    $unavailable = (Invoke-Api -Method "POST" -Path "/api/teacher-unavailable-times" -Body @{
            teacherId = $target.teacherId
            timeSlotId = $slot.id
            reason = "V5_STAGE5_SMOKE"
            status = 1
            remark = "auto-smoke"
        }).data
    $createdUnavailableId = $unavailable.id

    $task = (Invoke-Api -Method "POST" -Path "/api/v5/repair-tasks" -Body @{
            semesterId = 2
            planId = $PlanId
            sourcePlanId = $PlanId
            taskType = "RISK_REPAIR"
            title = "V5阶段5-教师禁排冒烟测试"
            triggerSource = "MANUAL"
            riskTypes = @("TEACHER_UNAVAILABLE")
            riskItemIds = @()
            scopePlanItemIds = @($target.id)
        }).data

    $generated = (Invoke-Api -Method "POST" -Path "/api/v5/repair-tasks/$($task.id)/suggestions/generate" -Body @{
            includeUnavailable = $false
            candidateLimit = 40
        }).data
    $types = @($generated | ForEach-Object { $_.suggestionType })

    $hasKeepRoomChangeTime = $types -contains "KEEP_ROOM_CHANGE_TIME"
    $hasManual = $types -contains "MANUAL_REVIEW"

    Write-Output ("taskId={0}" -f $task.id)
    Write-Output ("planItemId={0}" -f $target.id)
    Write-Output ("suggestionTypes={0}" -f ($types -join ","))
    if ($hasKeepRoomChangeTime) {
        Write-Output "PASS teacher-unavailable -> KEEP_ROOM_CHANGE_TIME generated"
    } elseif ($hasManual) {
        Write-Output "WARN teacher-unavailable generated MANUAL_REVIEW only"
    } else {
        Write-Output "FAIL expected KEEP_ROOM_CHANGE_TIME not generated"
        exit 1
    }
}
finally {
    if ($null -ne $createdUnavailableId) {
        try {
            Invoke-Api -Method "DELETE" -Path "/api/teacher-unavailable-times/$createdUnavailableId" | Out-Null
            Write-Output ("cleanup teacher-unavailable id={0}" -f $createdUnavailableId)
        } catch {
            Write-Warning ("cleanup failed id={0}: {1}" -f $createdUnavailableId, $_.Exception.Message)
        }
    }
}

