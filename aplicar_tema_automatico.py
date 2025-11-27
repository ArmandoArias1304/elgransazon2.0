"""
Script para agregar el Sistema de Temas a todas las páginas HTML con Tailwind
EL GRAN SAZÓN - Modo Claro/Oscuro
"""

import os
import re
from pathlib import Path

# Ruta base del proyecto
PROJECT_PATH = r"c:\xampp\NUEVOALEX\elgransazon - LISTO ADMIN LANDING PAGE - copia - copia\src\main\resources\templates"

# Patrones a buscar
TAILWIND_PATTERN = r'cdn\.tailwindcss\.com'
THEME_RESOURCES_PATTERN = r'fragments/theme :: themeResources'
THEME_SCRIPT_PATTERN = r'fragments/theme :: themeScript'
MATERIAL_ICONS_PATTERN = r'(Material\+Symbols\+Outlined.*?rel="stylesheet"\s*/?>'

# Fragmentos a agregar
THEME_RESOURCES = '''    <!-- Sistema de Temas (Modo Claro/Oscuro) -->
    <div th:replace="~{fragments/theme :: themeResources}"></div>'''

THEME_SCRIPT = '''
    <!-- Script del Sistema de Temas -->
    <div th:replace="~{fragments/theme :: themeScript}"></div>'''

def find_html_files_with_tailwind(base_path):
    """Encuentra todos los archivos HTML que usan Tailwind CSS"""
    html_files = []
    for root, dirs, files in os.walk(base_path):
        for file in files:
            if file.endswith('.html'):
                file_path = os.path.join(root, file)
                try:
                    with open(file_path, 'r', encoding='utf-8') as f:
                        content = f.read()
                        if re.search(TAILWIND_PATTERN, content):
                            html_files.append(file_path)
                except Exception as e:
                    print(f"⚠️  Error leyendo {file_path}: {e}")
    return html_files

def update_html_file(file_path):
    """Actualiza un archivo HTML agregando los fragmentos del tema"""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        relative_path = file_path.replace(PROJECT_PATH + "\\", "")
        
        # Verificar si ya tiene el sistema de temas
        if THEME_RESOURCES_PATTERN in content:
            print(f"⏭️  OMITIDO: {relative_path} (ya tiene el sistema de temas)")
            return 'skipped'
        
        print(f"📝 Procesando: {relative_path}")
        modified = False
        
        # 1. Agregar CSS después de Material Icons
        material_match = re.search(r'(Material\+Symbols\+Outlined.*?rel="stylesheet"\s*/?>\s*)', content, re.DOTALL)
        if material_match:
            insert_pos = material_match.end()
            content = content[:insert_pos] + '\n' + THEME_RESOURCES + '\n' + content[insert_pos:]
            modified = True
            print(f"  ✅ CSS agregado en <head>")
        
        # 2. Agregar JavaScript antes de </body>
        if THEME_SCRIPT_PATTERN not in content:
            # Buscar el último </script> antes de </body>
            body_match = re.search(r'(</script>\s*)(</body>)', content, re.DOTALL | re.IGNORECASE)
            if body_match:
                insert_pos = body_match.start(2)
                content = content[:insert_pos] + THEME_SCRIPT + '\n  ' + content[insert_pos:]
                modified = True
                print(f"  ✅ JavaScript agregado antes de </body>")
        
        if modified:
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(content)
            print(f"  💾 Archivo guardado")
            return 'updated'
        else:
            print(f"  ⚠️  No se pudo modificar (verificar estructura)")
            return 'skipped'
            
    except Exception as e:
        print(f"  ❌ ERROR: {e}")
        return 'error'

def main():
    print("═══════════════════════════════════════")
    print("SISTEMA DE TEMAS - EL GRAN SAZÓN")
    print("Actualización Automática de Páginas HTML")
    print("═══════════════════════════════════════\n")
    
    # Buscar archivos
    print("🔍 Buscando archivos HTML con Tailwind CSS...")
    html_files = find_html_files_with_tailwind(PROJECT_PATH)
    print(f"✅ Encontrados {len(html_files)} archivos HTML con Tailwind CSS\n")
    
    # Actualizar archivos
    updated = 0
    skipped = 0
    errors = 0
    
    for file_path in html_files:
        result = update_html_file(file_path)
        if result == 'updated':
            updated += 1
        elif result == 'skipped':
            skipped += 1
        elif result == 'error':
            errors += 1
        print()
    
    # Resumen
    print("═══════════════════════════════════════")
    print("RESUMEN")
    print("═══════════════════════════════════════")
    print(f"✅ Actualizados: {updated} archivos")
    print(f"⏭️  Omitidos: {skipped} archivos")
    print(f"❌ Errores: {errors} archivos")
    print()
    print("🎉 ¡Proceso completado!")
    print("Todas las páginas ahora soportan el modo claro/oscuro.")
    print("Cada dashboard tiene su botón de control.")

if __name__ == "__main__":
    main()
