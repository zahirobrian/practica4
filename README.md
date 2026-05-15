# Práctica 4 — Manejo de Archivos en Android

**Instituto Politécnico Nacional — Escuela Superior de Cómputo**  
Ingeniería en Sistemas Computacionales / 2020  
Unidad: Desarrollo de aplicaciones móviles nativas

---

## Introducción

Esta práctica desarrolla dos aplicaciones Android nativas en Kotlin enfocadas en el manejo avanzado de archivos. Ambas funcionan completamente **sin conexión a Internet** y son compatibles con **Android 7.0 (API 24)** o superior.

---

## Parte 1: File Manager IPN

Explorador de archivos completo para Android con soporte de temas institucionales.

### Funcionalidades

- **Exploración** de almacenamiento interno y externo
- **Navegación jerárquica** con breadcrumb interactivo
- **Visualizador** de texto plano, Markdown, JSON y XML con resaltado de sintaxis
- **Visualizador de imágenes** con zoom (pinch) y rotación (PhotoView + Glide)
- **Diálogo inteligente** para abrir archivos no soportados con otras apps
- **Metadatos**: tamaño, fecha de modificación y tipo MIME
- **Historial de recientes** persistido en SharedPreferences
- **Favoritos** con Room Database
- **Caché de miniaturas** para imágenes (Glide DiskCacheStrategy)
- **Operaciones**: copiar, mover, renombrar y eliminar con confirmación
- **Temas**: Guinda (IPN) y Azul (ESCOM), adaptados al modo claro/oscuro del sistema
- **Interfaz responsiva** para portrait y landscape

### Instalación

1. Clona el repositorio:
   ```bash
   git clone https://github.com/zahirobrian/practica4.git
   ```
2. Abre Android Studio → **Open** → selecciona `FileManagerIPN/`
3. Espera el Gradle sync
4. Conecta dispositivo o emulador (API 24+)
5. **Run 'app'**

### Permisos

La app solicita en tiempo de ejecución:
- `READ_EXTERNAL_STORAGE` (API < 33)
- `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO` (API ≥ 33)

### Arquitectura

```
MVVM
├── View          → Activities + Fragments + Adapter
├── ViewModel     → FileViewModel (LiveData, Coroutines)
└── Model         → Room (FavoriteFile, FavoriteDao, AppDatabase)
                    FileUtils (operaciones de archivo)
                    ThemeManager (SharedPreferences)
```

### Dependencias principales

| Librería | Uso |
|---|---|
| Room 2.6.1 | Base de datos favoritos |
| Glide 4.16.0 | Miniaturas de imágenes con caché |
| PhotoView 2.3.0 | Zoom y rotación de imágenes |
| Navigation Component 2.7.6 | Navegación entre fragments |
| Material Components 1.11.0 | Temas y UI |

---

## Implementación: Lectura y escritura de archivos

### Lectura de archivos de texto / JSON / XML
```kotlin
val content = file.readText(Charsets.UTF_8)
```

### Detección de tipo MIME
```kotlin
MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
```

### Resaltado de sintaxis
Se usa `SpannableString` con `ForegroundColorSpan` y expresiones regulares para colorear claves, valores y etiquetas.

### Operaciones de archivo
Todas las operaciones de escritura usan bloques try/catch y se ejecutan en `Dispatchers.IO` mediante corrutinas para no bloquear el hilo principal.

---

## Pruebas realizadas

| Dispositivo | API | Resultado |
|---|---|---|
| Pixel 4 (Emulador) | API 30 | ✅ Funcional |
| Pixel 7 (Emulador) | API 33 | ✅ Funcional |
| Samsung Galaxy A32 | API 31 | ✅ Funcional |

---

## Conclusiones

El desarrollo de esta práctica permitió:
- Comprender el manejo de permisos en distintas versiones de Android (Scoped Storage vs permisos legacy)
- Implementar persistencia local con Room y SharedPreferences
- Aplicar el patrón MVVM con LiveData y Corrutinas para operaciones asíncronas de archivo
- Trabajar con librerías de terceros para visualización multimedia (Glide, PhotoView)

---

## Bibliografía

- Android Developers. (2024). *Room persistence library*. https://developer.android.com/training/data-storage/room
- Android Developers. (2024). *Storage overview*. https://developer.android.com/training/data-storage
- Android Developers. (2024). *Navigation component*. https://developer.android.com/guide/navigation
- Bumptech. (2024). *Glide v4 documentation*. https://bumptech.github.io/glide/
- Chrisbanes. (2020). *PhotoView*. https://github.com/chrisbanes/PhotoView

---

**Fecha de entrega:** 15 de mayo de 2026
