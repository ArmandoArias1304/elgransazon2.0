"""
Script para arreglar el formato del fragmento themeResources
Separa correctamente el </div> del <script>
"""

import os
import re
from pathlib import Path

PROJECT_PATH = r"c:\xampp\NUEVOALEX\elgransazon - LISTO ADMIN LANDING PAGE - copia - copia\src\main\resources\templates"

def fix_theme_resources_format(file_path):
    """Arregla el formato del fragmento themeResources"""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        relative_path = file_path.replace(PROJECT_PATH + "\\", "")
        
        # Buscar el patrón problemático
        if 'themeResources}"></div>\n<script' not in content and \
           'themeResources}"></div>\r\n<script' not in content:
            print(f"⏭️  {relative_path} - OK")
            return False
        
        print(f"🔧 Arreglando: {relative_path}")
        
        # Reemplazar el patrón incorrecto
        content = re.sub(
            r'(themeResources}"></div>)\s*\n\s*(<script)',
            r'\1\n    \2',
            content
        )
        
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        
        print(f"  ✅ Formato corregido")
        return True
            
    except Exception as e:
        print(f"  ❌ ERROR: {e}")
        return False

def main():
    print("═══════════════════════════════════════")
    print("ARREGLAR FORMATO DE FRAGMENTOS")
    print("═══════════════════════════════════════\n")
    
    fixed = 0
    
    for root, dirs, files in os.walk(PROJECT_PATH):
        for file in files:
            if file.endswith('.html'):
                file_path = os.path.join(root, file)
                if fix_theme_resources_format(file_path):
                    fixed += 1
    
    print(f"\n═══════════════════════════════════════")
    print(f"✅ Archivos corregidos: {fixed}")
    print("═══════════════════════════════════════")

if __name__ == "__main__":
    main()
