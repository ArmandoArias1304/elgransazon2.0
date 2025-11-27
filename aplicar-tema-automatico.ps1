# Script para agregar el Sistema de Temas a todas las páginas HTML con Tailwind
# EL GRAN SAZÓN - Modo Claro/Oscuro

$ErrorActionPreference = "Stop"

# Ruta base del proyecto
$projectPath = "c:\xampp\NUEVOALEX\elgransazon - LISTO ADMIN LANDING PAGE - copia - copia\src\main\resources\templates"

# Buscar todos los archivos HTML que tengan Tailwind
$htmlFiles = Get-ChildItem -Path $projectPath -Recurse -Filter "*.html" | 
    Where-Object { (Get-Content $_.FullName -Raw) -match "cdn\.tailwindcss\.com" }

Write-Host "Encontrados $($htmlFiles.Count) archivos HTML con Tailwind CSS" -ForegroundColor Cyan
Write-Host ""

$updated = 0
$skipped = 0
$errors = 0

foreach ($file in $htmlFiles) {
    try {
        $content = Get-Content $file.FullName -Raw -Encoding UTF8
        $relativePath = $file.FullName.Replace($projectPath + "\", "")
        
        # Verificar si ya tiene el fragmento de tema
        if ($content -match 'fragments/theme :: themeResources') {
            Write-Host "⏭️  OMITIDO: $relativePath (ya tiene el sistema de temas)" -ForegroundColor Yellow
            $skipped++
            continue
        }
        
        Write-Host "📝 Procesando: $relativePath" -ForegroundColor White
        
        $modified = $false
        
        # 1. Agregar CSS en el <head> después de Material Icons
        if ($content -match '(?s)(Material\+Symbols\+Outlined.*?rel="stylesheet"\s*/>)') {
            $content = $content -replace '(Material\+Symbols\+Outlined.*?rel="stylesheet"\s*/>)', 
                "`$1`r`n    <!-- Sistema de Temas (Modo Claro/Oscuro) -->`r`n    <div th:replace=`"~{fragments/theme :: themeResources}`"></div>"
            $modified = $true
            Write-Host "  ✅ CSS agregado en <head>" -ForegroundColor Green
        }
        
        # 2. Agregar JavaScript antes de </body>
        if ($content -match '(?s)(</script>\s*)(</body>)') {
            # Si no tiene ya el script del tema
            if (-not ($content -match 'fragments/theme :: themeScript')) {
                $content = $content -replace '(</script>\s*)(</body>)', 
                    "`$1`r`n    `r`n    <!-- Script del Sistema de Temas -->`r`n    <div th:replace=`"~{fragments/theme :: themeScript}`"></div>`r`n  `$2"
                $modified = $true
                Write-Host "  ✅ JavaScript agregado antes de </body>" -ForegroundColor Green
            }
        }
        
        if ($modified) {
            # Guardar archivo
            Set-Content -Path $file.FullName -Value $content -Encoding UTF8 -NoNewline
            Write-Host "  💾 Archivo guardado" -ForegroundColor Green
            $updated++
        } else {
            Write-Host "  ⚠️  No se pudo modificar (verificar estructura)" -ForegroundColor Yellow
            $skipped++
        }
        
    } catch {
        Write-Host "  ❌ ERROR: $_" -ForegroundColor Red
        $errors++
    }
    
    Write-Host ""
}

Write-Host "═══════════════════════════════════════" -ForegroundColor Cyan
Write-Host "RESUMEN" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════" -ForegroundColor Cyan
Write-Host "✅ Actualizados: $updated archivos" -ForegroundColor Green
Write-Host "⏭️  Omitidos: $skipped archivos" -ForegroundColor Yellow
Write-Host "❌ Errores: $errors archivos" -ForegroundColor Red
Write-Host ""
Write-Host "🎉 ¡Proceso completado!" -ForegroundColor Cyan
Write-Host "Ahora todas las páginas tendrán el modo claro/oscuro sincronizado." -ForegroundColor White
Write-Host "El botón solo está en admin/dashboard.html para cambiar el tema en todas las páginas." -ForegroundColor White
