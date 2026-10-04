param()
# Generate legacy launcher PNGs from the vector spec
# (res/drawable/ic_launcher_foreground.xml, 108x108 viewport).
# Rounded corners are approximated with polygon chord points (3 per corner),
# NOT GDI+ AddArc - arc angle conventions proved unreliable here.
Add-Type -AssemblyName System.Drawing

function RoundedRectPoly([double]$x, [double]$y, [double]$w, [double]$h, [double]$r, [double]$s) {
    # clockwise outline, math angle converted to y-down: px = cx + r*cos(t), py = cy - r*sin(t)
    $list = New-Object 'System.Collections.Generic.List[System.Drawing.PointF]'
    # top-left corner (cx=x+r, cy=y+r): t=180,135,90
    $cx = $x + $r; $cy = $y + $r
    foreach ($t in 180, 135, 90) {
        $a = $t * [Math]::PI / 180
        $list.Add((New-Object System.Drawing.PointF ([single](($cx + $r*[Math]::Cos($a))*$s)), ([single](($cy - $r*[Math]::Sin($a))*$s))))
    }
    # top-right corner (cx=x+w-r, cy=y+r): t=90,45,0
    $cx = $x + $w - $r
    foreach ($t in 90, 45, 0) {
        $a = $t * [Math]::PI / 180
        $list.Add((New-Object System.Drawing.PointF ([single](($cx + $r*[Math]::Cos($a))*$s)), ([single](($cy - $r*[Math]::Sin($a))*$s))))
    }
    # bottom-right corner (cx=x+w-r, cy=y+h-r): t=0,-45,-90
    $cy = $y + $h - $r
    foreach ($t in 0, -45, -90) {
        $a = $t * [Math]::PI / 180
        $list.Add((New-Object System.Drawing.PointF ([single](($cx + $r*[Math]::Cos($a))*$s)), ([single](($cy - $r*[Math]::Sin($a))*$s))))
    }
    # bottom-left corner (cx=x+r, cy=y+h-r): t=-90,-135,-180
    $cx = $x + $r
    foreach ($t in -90, -135, -180) {
        $a = $t * [Math]::PI / 180
        $list.Add((New-Object System.Drawing.PointF ([single](($cx + $r*[Math]::Cos($a))*$s)), ([single](($cy - $r*[Math]::Sin($a))*$s))))
    }
    return $list.ToArray()
}

function New-Icon([int]$size, [string]$path) {
    $s = $size / 108.0
    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear([System.Drawing.Color]::FromArgb(0xFF, 0x0B, 0x57, 0xD0))
    $wb = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)

    # white ticket body 27,26 54x44 r6
    $bodyPts = RoundedRectPoly 27 26 54 44 6 $s
    $g.FillPolygon($wb, $bodyPts)

    # two legs at the bottom
    foreach ($leg in @(@(@(36,70), @(40,80), @(46,80), @(42,70)), @(@(66,70), @(72,80), @(66,80), @(62,70)))) {
        $arr = New-Object 'System.Drawing.PointF[]' 4
        for ($i = 0; $i -lt 4; $i++) {
            $arr[$i] = New-Object System.Drawing.PointF ([single]($leg[$i][0]*$s)), ([single]($leg[$i][1]*$s))
        }
        $g.FillPolygon($wb, $arr)
    }

    # yellow tag 43,14 22x13 r3
    $tagPts = RoundedRectPoly 43 14 22 13 3 $s
    $yb = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(0xFF, 0xFF, 0xD5, 0x4F))
    $g.FillPolygon($yb, $tagPts)

    $wb.Dispose(); $yb.Dispose()
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
    if (Test-Path $path) { Write-Host "OK $path ($size px)" } else { Write-Host "FAIL $path" }
}

$res = "C:\Users\ADMIN\Desktop\yuecheng\app\src\main\res"
New-Icon 48  "$res\mipmap-mdpi\ic_launcher.png"
New-Icon 72  "$res\mipmap-hdpi\ic_launcher.png"
New-Icon 96  "$res\mipmap-xhdpi\ic_launcher.png"
New-Icon 144 "$res\mipmap-xxhdpi\ic_launcher.png"
New-Icon 192 "$res\mipmap-xxxhdpi\ic_launcher.png"
