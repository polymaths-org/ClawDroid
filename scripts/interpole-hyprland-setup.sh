#!/usr/bin/env bash
# Interpole Arch Hyprland easy-mode setup (run ON THE DESKTOP, not the phone).
# Installs input + screenshot deps and verifies hyprctl access.
set -euo pipefail

echo "==> Installing packages (Arch)"
sudo pacman -S --needed ydotool wtype grim slurp wl-clipboard socat

echo "==> Enabling ydotoold (synthetic input daemon for Wayland)"
if systemctl --user enable --now ydotoold.service 2>/dev/null; then
  echo "ydotoold user service started"
else
  echo "user service unavailable, trying system service"
  sudo systemctl enable --now ydotoold
fi

echo "==> Verifying"
hyprctl version
ydotool --help | head -5
grim -h | head -3 || true

echo ""
echo "OK. In ClawDroid: Settings > Interpole > Enable Interpole + Hyprland easy-mode ON."
echo "To disable Hyprland specifics (generic Wayland/X11 only), toggle Hyprland easy-mode OFF."
echo "To kill desktop control entirely, toggle Enable Interpole OFF."
