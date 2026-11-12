#!/usr/bin/env bash
# Ayudas para probar la app en el emulador/dispositivo sin interfaz gráfica de ADB.
# Pensado para Git Bash en Windows (MSYS_NO_PATHCONV evita que reescriba rutas /sdcard).
#
# Uso:
#   scripts/adb-ui.sh dump [filtro]        lista textos/descripciones y sus coordenadas (uiautomator)
#   scripts/adb-ui.sh tap "<texto>" [n]    toca el elemento cuyo texto o content-desc es exactamente <texto>
#                                          (n = n-ésima coincidencia, por defecto 1)
#   scripts/adb-ui.sh shot <nombre>        captura de pantalla en build/shots/<nombre>.png
#   scripts/adb-ui.sh type "<texto>"       escribe texto (los espacios se convierten en %s)
#   scripts/adb-ui.sh prep                 desactiva el tutorial de stylus del teclado (si no, captura el texto)
#   scripts/adb-ui.sh offline | online     apaga / enciende Wi-Fi y datos (prueba sin conexión)
#
# Variables: ADB (ruta de adb.exe), OMS_SHOTS (carpeta de capturas).
export MSYS_NO_PATHCONV=1
ADB="${ADB:-$HOME/AppData/Local/Android/Sdk/platform-tools/adb.exe}"
SHOTS="${OMS_SHOTS:-$(cd "$(dirname "$0")/.." && pwd)/build/shots}"

dump() {
  $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  $ADB shell cat /sdcard/ui.xml | grep -o '<node [^>]*>'
}

case "$1" in
  dump)
    dump | grep -o '\(text\|content-desc\)="[^"]\+"[^>]*bounds="[^"]*"' \
      | sed 's/ \(resource-id\|class\|package\|checkable\|checked\|clickable\|enabled\|focusable\|focused\|scrollable\|long-clickable\|password\|selected\|index\|hint\|tooltip-text\|drawing-order\)="[^"]*"//g' \
      | grep -i "${2:-.}"
    ;;
  tap)
    n=${3:-1}
    b=$(dump | grep -F -e "text=\"$2\"" -e "content-desc=\"$2\"" | sed -n "${n}p" \
        | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+')
    [ -z "$b" ] && { echo "NO ENCONTRADO: $2"; exit 1; }
    set -- $b
    x=$(( ($1 + $3) / 2 )); y=$(( ($2 + $4) / 2 ))
    $ADB shell input tap $x $y && echo "tap $x,$y"
    ;;
  shot)
    mkdir -p "$SHOTS" && $ADB exec-out screencap -p > "$SHOTS/$2.png" && echo "$SHOTS/$2.png"
    ;;
  type)
    $ADB shell "input text '${2// /%s}'"
    ;;
  prep)
    $ADB shell settings put secure stylus_handwriting_enabled 0
    ;;
  offline)
    $ADB shell svc wifi disable; $ADB shell svc data disable ;;
  online)
    $ADB shell svc wifi enable; $ADB shell svc data enable ;;
  *)
    sed -n '2,15p' "$0" ;;
esac
