# Práctica 4 - Manejo de Archivos en Android

## Descripción
Archivo Markdown de prueba para el **File Manager IPN**.

## Tecnologías Utilizadas

- **Kotlin** — Lenguaje principal
- **Room Database** — Persistencia de favoritos
- **Glide** — Carga y caché de imágenes
- **PhotoView** — Zoom y rotación de imágenes
- **Navigation Component** — Navegación entre pantallas
- **Material Design 3** — Componentes visuales

## Arquitectura MVVM

```
View → ViewModel → Model
```

| Capa | Componentes |
|------|-------------|
| View | MainActivity, Fragments, FileAdapter |
| ViewModel | FileViewModel, LiveData |
| Model | Room, FileUtils, ThemeManager |

## Temas

### Tema Guinda (IPN)
Color primario: `#731D3F`

### Tema Azul (ESCOM)  
Color primario: `#003B8E`

## Conclusión
Esta práctica demuestra el manejo avanzado de archivos en Android,
incluyendo lectura, escritura, visualización y persistencia local.
