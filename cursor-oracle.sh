#!/bin/bash
# Запуск Cursor с TNS_ADMIN для SQLTools Oracle (wallet).
# Использование: ./cursor-oracle.sh   или   ./cursor-oracle.sh /path/to/project

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export TNS_ADMIN="${TNS_ADMIN:-$SCRIPT_DIR/wallet}"

if [ ! -d "$TNS_ADMIN" ]; then
  echo "Warning: Wallet folder not found: $TNS_ADMIN"
  echo "Unzip Wallet_*.zip into ./wallet or set TNS_ADMIN yourself."
fi

exec cursor "$@"
