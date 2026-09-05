#!/bin/bash
CHROME_BIN=$(find /opt/ms-playwright -name chrome -type f -perm /111 2>/dev/null | head -n 1)
if [ -z "$CHROME_BIN" ]; then
    echo "Error: Chromium executable not found in /opt/ms-playwright" >&2
    exit 1
fi
for arg in "$@"; do
    if [ "$arg" = "--version" ] || [ "$arg" = "-v" ]; then
        exec "$CHROME_BIN" "$@"
    fi
done
EXTRA_ARGS=()
case " $* " in
    *" --no-sandbox "*) ;;
    *) EXTRA_ARGS+=("--no-sandbox") ;;
esac
case " $* " in
    *" --disable-dev-shm-usage "*) ;;
    *) EXTRA_ARGS+=("--disable-dev-shm-usage") ;;
esac
if [ -z "$DISPLAY" ]; then
    case " $* " in
        *" --headless"*) ;;
        *) EXTRA_ARGS+=("--headless=new") ;;
    esac
fi
exec "$CHROME_BIN" "${EXTRA_ARGS[@]}" "$@"
