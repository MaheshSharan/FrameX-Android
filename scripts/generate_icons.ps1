Add-Type -AssemblyName System.Drawing
$sourcePath = 'D:\Android\FrameX\logo.png'
$baseDir = 'D:\Android\FrameX\app\src\main\res'

$sizes = @(
    @{name='mdpi'; size=48},
    @{name='hdpi'; size=72},
    @{name='xhdpi'; size=96},
    @{name='xxhdpi'; size=144},
    @{name='xxxhdpi'; size=192}
)

$img = [System.Drawing.Image]::FromFile($sourcePath)

foreach ($item in $sizes) {
    $folder = $item.name
    $size = $item.size

    $targetFolder = Join-Path -Path $baseDir -ChildPath ("mipmap-" + $folder)
    if (-Not (Test-Path -Path $targetFolder)) {
        New-Item -ItemType Directory -Path $targetFolder | Out-Null
    }

    $bmp = New-Object System.Drawing.Bitmap($size, $size)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    
    $g.DrawImage($img, 0, 0, $size, $size)
    $g.Dispose()

    $bmp.Save((Join-Path -Path $targetFolder -ChildPath 'ic_launcher.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Save((Join-Path -Path $targetFolder -ChildPath 'ic_launcher_round.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

$img.Dispose()
Write-Host "Icons generated successfully."
