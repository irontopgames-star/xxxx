# Herramientas de recursos

Requieren Python 3 con `pillow`, `numpy` y `scipy` (`pip install pillow numpy scipy`).

- `build_bear.py`: convierte `oso.bbmodel` + su textura en la malla riggeada de Bearfriender,
  sus animaciones y las imágenes de la pantalla de la panza.
  `python tools/build_bear.py oso.bbmodel textura.png src/main/resources`
  Los huesos, pivotes y pesos se definen en la cabecera y en `core_labels()`.
  Las animaciones están en `bear_anims.py` y las pantallas en `screens.py`.
- `generate_assets.py`: genera el modelo, animaciones y textura del osito destruido (GeckoLib).
- `check_layout.py`: simula el plano de la instalación y comprueba conectividad (salas, anclas, sótano,
  túnel de salida) y que el mobiliario no bloquea puertas. Útil tras cambiar `FacilityLayout` o `furnish()`.
- `build_decor.py`: genera los bloques decorativos (televisor CRT, peluches, globos, cubos de juguete):
  modelos, estados de bloque, texturas, modelos de objeto, tablas de botín y textos de idioma.
  `python tools/build_decor.py src/main/resources`
- `bear_face.py`: renderiza el rostro de Bearfriender desde su malla y textura y lo convierte en las imágenes de
  pantalla CRT (televisores y panza). Lo usa `build_bear.py`.
- `build_sounds.py`: sintetiza los sonidos originales del mod (necesita ffmpeg) y escribe `sounds.json`.
  **Para usar tus propios sonidos**, reemplaza los `.ogg` de `assets/bearfriender/sounds/` conservando el nombre
  (mono, Vorbis). Para añadir variantes, copia el archivo y lista su nombre en `sounds.json`.
