#!/usr/bin/env python3
"""
AIO Remote — Desktop Companion Receiver
Supports Windows, macOS, and Linux (X11 via pynput, Wayland touchpad via the desktop portal).

Protocol: JSON over WebSocket on port 8765 (ws://<host>:8765/ws)
Service Discovery: mDNS (_slateremote._tcp.local.)
Security: One-time 6-digit pairing code -> HMAC-SHA256 per-device token authentication.
"""

import asyncio
from contextlib import suppress
import hashlib
import hmac
import html
import json
import os
import platform
import secrets
import shutil
import socket
import subprocess
import sys
import time
from pathlib import Path
from typing import Dict, Optional, Set

try:
    import websockets
except ImportError:
    print("Missing dependency: pip install websockets pynput zeroconf")
    sys.exit(1)

try:
    from zeroconf import ServiceInfo, Zeroconf
except ImportError:
    Zeroconf = None
    ServiceInfo = None

try:
    from pynput.keyboard import Controller as KeyController, Key
    from pynput.mouse import Button as MouseBtn, Controller as MouseController
except ImportError:
    KeyController = None
    MouseController = None
    Key = None
    MouseBtn = None


TRUSTED_STORE = Path.home() / ".slate_remote_trusted.json"
DEFAULT_PORT = 8765


def detect_os_name() -> str:
    sys_name = platform.system().upper()
    if "WINDOWS" in sys_name:
        return "WINDOWS"
    if "DARWIN" in sys_name:
        return "MACOS"
    if "LINUX" in sys_name:
        return "LINUX"
    return "UNKNOWN"


def is_wayland_session() -> bool:
    return (
        os.environ.get("XDG_SESSION_TYPE", "").lower() == "wayland"
        or bool(os.environ.get("WAYLAND_DISPLAY"))
    )


class InputBackend:
    """Platform-aware input injector with safe release-all on disconnect."""

    def __init__(self) -> None:
        self.os_name = detect_os_name()
        self.wayland = self.os_name == "LINUX" and is_wayland_session()
        self.has_ydotool = shutil.which("ydotool") is not None
        self.mouse = MouseController() if MouseController and not self.wayland else None
        self.keyboard = KeyController() if KeyController and not self.wayland else None
        self.held_buttons: Set[str] = set()


    def move_pointer(self, dx: int, dy: int) -> None:
        if self.mouse:
            self.mouse.move(dx, dy)

    def mouse_click(self, button: str, count: int = 1) -> None:
        if self.mouse and MouseBtn:
            btn = {"LEFT": MouseBtn.left, "RIGHT": MouseBtn.right, "MIDDLE": MouseBtn.middle}.get(
                button, MouseBtn.left
            )
            self.mouse.click(btn, count)

    def mouse_down(self, button: str) -> None:
        self.held_buttons.add(button)
        if self.mouse and MouseBtn:
            btn = {"LEFT": MouseBtn.left, "RIGHT": MouseBtn.right, "MIDDLE": MouseBtn.middle}.get(
                button, MouseBtn.left
            )
            self.mouse.press(btn)

    def mouse_up(self, button: str) -> None:
        self.held_buttons.discard(button)
        if self.mouse and MouseBtn:
            btn = {"LEFT": MouseBtn.left, "RIGHT": MouseBtn.right, "MIDDLE": MouseBtn.middle}.get(
                button, MouseBtn.left
            )
            self.mouse.release(btn)

    def scroll(self, dx: int, dy: int) -> None:
        if self.mouse:
            self.mouse.scroll(dx, dy)

    def type_text(self, text: str) -> None:
        if self.wayland and self.has_ydotool:
            subprocess.run(["ydotool", "type", "--", text], check=False)
        elif self.keyboard:
            self.keyboard.type(text)

    def key_tap(self, key_name: str, modifiers: int) -> None:
        if not self.keyboard or not Key:
            return
        special = {
            "ENTER": Key.enter,
            "ESCAPE": Key.esc,
            "BACKSPACE": Key.backspace,
            "TAB": Key.tab,
            "SPACE": Key.space,
            "DELETE": Key.delete,
            "UP_ARROW": Key.up,
            "DOWN_ARROW": Key.down,
            "LEFT_ARROW": Key.left,
            "RIGHT_ARROW": Key.right,
            "HOME": Key.home,
            "END": Key.end,
            "PAGE_UP": Key.page_up,
            "PAGE_DOWN": Key.page_down,
            "F1": Key.f1, "F2": Key.f2, "F3": Key.f3, "F4": Key.f4,
            "F5": Key.f5, "F6": Key.f6, "F7": Key.f7, "F8": Key.f8,
            "F9": Key.f9, "F10": Key.f10, "F11": Key.f11, "F12": Key.f12,
        }
        target = special.get(key_name, key_name.lower() if len(key_name) == 1 else None)
        if target is None:
            return

        mod_keys = []
        if modifiers & (1 << 0):
            mod_keys.append(Key.ctrl_l)
        if modifiers & (1 << 1):
            mod_keys.append(Key.shift_l)
        if modifiers & (1 << 2):
            mod_keys.append(Key.alt_l)
        if modifiers & (1 << 3):
            mod_keys.append(Key.cmd)

        for mk in mod_keys:
            self.keyboard.press(mk)
        try:
            self.keyboard.press(target)
            self.keyboard.release(target)
        finally:
            for mk in reversed(mod_keys):
                self.keyboard.release(mk)

    def media_control(self, action: str) -> None:
        if not self.keyboard or not Key:
            return
        mapping = {
            "PLAY_PAUSE": getattr(Key, "media_play_pause", None),
            "NEXT": getattr(Key, "media_next", None),
            "PREVIOUS": getattr(Key, "media_previous", None),
            "VOLUME_UP": getattr(Key, "media_volume_up", None),
            "VOLUME_DOWN": getattr(Key, "media_volume_down", None),
            "MUTE": getattr(Key, "media_volume_mute", None),
        }
        key = mapping.get(action)
        if key:
            self.keyboard.press(key)
            self.keyboard.release(key)

    def release_all(self) -> None:
        for btn in list(self.held_buttons):
            self.mouse_up(btn)
        self.held_buttons.clear()


class TrustedDeviceRegistry:
    def __init__(self) -> None:
        self.tokens: Dict[str, Dict[str, str]] = {}
        self.load()

    def load(self) -> None:
        if TRUSTED_STORE.exists():
            try:
                self.tokens = json.loads(TRUSTED_STORE.read_text(encoding="utf-8"))
            except Exception:
                self.tokens = {}

    def save(self) -> None:
        TRUSTED_STORE.write_text(json.dumps(self.tokens, indent=2), encoding="utf-8")

    def add_device(self, device_id: str, device_name: str, token: str) -> None:
        self.tokens[device_id] = {"name": device_name, "token": token}
        self.save()

    def get_token(self, device_id: str) -> Optional[str]:
        entry = self.tokens.get(device_id)
        return entry["token"] if entry else None


class SlateReceiverServer:
    def __init__(self, port: int = DEFAULT_PORT) -> None:
        self.port = port
        self.server_id = secrets.token_hex(6)
        self.server_name = socket.gethostname()
        self.os_name = detect_os_name()
        self.pairing_code = f"{secrets.randbelow(900000) + 100000}"
        self.registry = TrustedDeviceRegistry()
        self.injector = InputBackend()
        self.approval_dialog = shutil.which("zenity")
        self.approval_pending = False

    async def approve_phone(self, name: str, address: str) -> bool:
        if not self.approval_dialog or self.approval_pending:
            return False
        self.approval_pending = True
        try:
            message = html.escape(f"Allow {name[:80]} ({address}) to control this computer?")
            dialog = await asyncio.create_subprocess_exec(
                self.approval_dialog, "--question", "--timeout=25", "--title=AIO Remote",
                f"--text={message}", "--ok-label=Allow", "--cancel-label=Deny",
            )
            return await dialog.wait() == 0
        except OSError:
            return False
        finally:
            self.approval_pending = False

    async def handle_client(self, ws) -> None:
        nonce = secrets.token_hex(16)
        authenticated = False
        pointer = None
        pointer_setup = None

        async def notify_pointer_error(message):
            print(f"[POINTER] {message}", file=sys.stderr)
            with suppress(websockets.exceptions.ConnectionClosed):
                await ws.send(json.dumps({
                    "type": "ERROR", "code": "POINTER_UNAVAILABLE", "message": message,
                }))

        async def open_pointer():
            nonlocal pointer
            try:
                from wayland_pointer import WaylandPointer
                controller = WaylandPointer()
                try:
                    await asyncio.wait_for(controller.start(), timeout=90)
                except BaseException:
                    await controller.close()
                    raise
                print("[POINTER] Desktop granted remote pointer access.")
                pointer = controller
            except ImportError:
                await notify_pointer_error("Install dbus-next on the laptop to enable the Wayland touchpad.")
            except PermissionError as exc:
                await notify_pointer_error(str(exc))
            except Exception as exc:
                await notify_pointer_error(f"Could not start the desktop touchpad session: {exc}")

        await ws.send(json.dumps({
            "type": "AUTH_CHALLENGE",
            "serverId": self.server_id,
            "serverName": self.server_name,
            "os": self.os_name,
            "nonce": nonce,
            "desktopApproval": self.approval_dialog is not None,
        }))

        try:
            async for raw in ws:
                data = json.loads(raw)
                msg_type = data.get("type")

                if not authenticated:
                    if msg_type == "APPROVAL_REQUEST":
                        dev_id = str(data.get("deviceId", ""))
                        dev_name = str(data.get("deviceName", "Android phone"))
                        address = str(ws.remote_address[0]) if ws.remote_address else "unknown address"
                        approved = bool(dev_id) and await self.approve_phone(dev_name, address)
                        reply = {"type": "PAIR_RESULT", "success": approved, "os": self.os_name}
                        if approved:
                            token = secrets.token_hex(32)
                            self.registry.add_device(dev_id, dev_name, token)
                            authenticated = True
                            reply.update(token=token, serverName=self.server_name)
                        else:
                            reply["message"] = "Connection approval was denied, busy, or timed out."
                        await ws.send(json.dumps(reply))
                    elif msg_type == "PAIR_REQUEST":
                        code = str(data.get("code", "")).strip()
                        dev_id = str(data.get("deviceId", ""))
                        dev_name = str(data.get("deviceName", "Android Phone"))
                        if code == self.pairing_code and dev_id:
                            token = secrets.token_hex(32)
                            self.registry.add_device(dev_id, dev_name, token)
                            authenticated = True
                            self.pairing_code = f"{secrets.randbelow(900000) + 100000}"
                            print(f"[PAIRED] {dev_name} ({dev_id}). Next pairing code: {self.pairing_code}")
                            await ws.send(json.dumps({
                                "type": "PAIR_RESULT",
                                "success": True,
                                "token": token,
                                "serverName": self.server_name,
                                "os": self.os_name,
                            }))
                        else:
                            await ws.send(json.dumps({
                                "type": "PAIR_RESULT",
                                "success": False,
                                "os": self.os_name,
                                "message": "Invalid 6-digit pairing code.",
                            }))
                    elif msg_type == "AUTH_VERIFY":
                        dev_id = str(data.get("deviceId", ""))
                        client_hmac = str(data.get("hmac", ""))
                        token = self.registry.get_token(dev_id)
                        if token:
                            expected = hmac.new(
                                token.encode("utf-8"),
                                nonce.encode("utf-8"),
                                hashlib.sha256,
                            ).hexdigest()
                            if hmac.compare_digest(expected, client_hmac):
                                authenticated = True
                                print(f"[CONNECTED] Trusted device {dev_id}")
                                await ws.send(json.dumps({
                                    "type": "AUTH_RESULT",
                                    "success": True,
                                    "serverName": self.server_name,
                                    "os": self.os_name,
                                }))
                                continue
                        await ws.send(json.dumps({
                            "type": "AUTH_RESULT",
                            "success": False,
                            "os": self.os_name,
                            "message": "Unknown or revoked device token.",
                        }))
                    continue

                if self.injector.wayland:
                    action = data.get("type")
                    if action in {"POINTER_MOVE", "POINTER_CLICK", "POINTER_DOWN", "POINTER_UP", "POINTER_SCROLL"}:
                        if pointer_setup is None:
                            pointer_setup = asyncio.create_task(open_pointer())
                        if pointer:
                            try:
                                await pointer.dispatch(data)
                            except RuntimeError as exc:
                                await pointer.close()
                                pointer = None
                                await notify_pointer_error(f"Laptop pointer session ended. Reconnect to try again. ({exc})")
                        continue
                    if action == "RELEASE_ALL" and pointer:
                        with suppress(RuntimeError):
                            await pointer.dispatch(data)
                self.dispatch_command(data, ws)
        finally:
            if pointer_setup is not None:
                if not pointer_setup.done():
                    pointer_setup.cancel()
                with suppress(asyncio.CancelledError):
                    await pointer_setup
            if pointer:
                await pointer.close()
            if authenticated:
                self.injector.release_all()

    def dispatch_command(self, data: dict, ws) -> None:
        msg_type = data.get("type")
        seq = data.get("seq", 0)
        ts = data.get("ts", int(time.time() * 1000))

        if msg_type == "POINTER_MOVE":
            self.injector.move_pointer(int(data.get("dx", 0)), int(data.get("dy", 0)))
        elif msg_type == "POINTER_CLICK":
            self.injector.mouse_click(str(data.get("button", "LEFT")), int(data.get("count", 1)))
        elif msg_type == "POINTER_DOWN":
            self.injector.mouse_down(str(data.get("button", "LEFT")))
        elif msg_type == "POINTER_UP":
            self.injector.mouse_up(str(data.get("button", "LEFT")))
        elif msg_type == "POINTER_SCROLL":
            self.injector.scroll(int(data.get("dx", 0)), int(data.get("dy", 0)))
        elif msg_type == "TEXT_INPUT":
            self.injector.type_text(str(data.get("text", "")))
        elif msg_type == "KEY_TAP":
            self.injector.key_tap(str(data.get("key", "")), int(data.get("modifiers", 0)))
        elif msg_type == "MEDIA_CONTROL":
            self.injector.media_control(str(data.get("action", "")))
        elif msg_type == "PRESENTATION_COMMAND":
            action = str(data.get("action", ""))
            key_map = {
                "NEXT_SLIDE": "RIGHT_ARROW",
                "PREVIOUS_SLIDE": "LEFT_ARROW",
                "START_PRESENTATION": "F5",
                "END_PRESENTATION": "ESCAPE",
                "BLACK_SCREEN": "B",
                "WHITE_SCREEN": "W",
            }
            if action in key_map:
                self.injector.key_tap(key_map[action], 0)
        elif msg_type == "RELEASE_ALL":
            self.injector.release_all()
        elif msg_type == "PING":
            asyncio.create_task(ws.send(json.dumps({"type": "PONG", "seq": seq, "ts": ts})))


def advertise_mdns(port: int, server_name: str, os_name: str) -> Optional[Zeroconf]:
    if not Zeroconf or not ServiceInfo:
        return None
    zc = None
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as route:
            route.connect(("224.0.0.251", 5353))
            ip = route.getsockname()[0]
        if ip.startswith("127.") or ip == "0.0.0.0":
            return None
        zc = Zeroconf(interfaces=[ip])
        info = ServiceInfo(
            "_slateremote._tcp.local.",
            f"{server_name}._slateremote._tcp.local.",
            addresses=[socket.inet_aton(ip)],
            port=port,
            properties={"os": os_name},
        )
        zc.register_service(info)
        return zc
    except Exception:
        if zc is not None:
            zc.close()
        return None


async def udp_discovery_loop(port: int, server_name: str, os_name: str) -> None:
    """Responds to SLATE_DISCOVER_V1 LAN broadcasts on UDP 8766 with host name & OS."""
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    sock.bind(("0.0.0.0", 8766))
    sock.setblocking(False)
    loop = asyncio.get_running_loop()
    payload = json.dumps({
        "service": "slate-remote",
        "name": server_name,
        "os": os_name,
        "port": port,
    }).encode("utf-8")

    try:
        while True:
            try:
                data, addr = await loop.sock_recvfrom(sock, 256)
                if data.startswith(b"SLATE_DISCOVER_V1"):
                    await loop.sock_sendto(sock, payload, addr)
            except asyncio.CancelledError:
                break
            except Exception:
                await asyncio.sleep(0.2)
    finally:
        sock.close()


async def main() -> None:
    server = SlateReceiverServer()
    zc = await asyncio.to_thread(advertise_mdns, server.port, server.server_name, server.os_name)
    discovery_task = asyncio.create_task(
        udp_discovery_loop(server.port, server.server_name, server.os_name)
    )
    print("==========================================")
    print(" AIO Remote — Desktop Companion Receiver")
    print(f" Host: {server.server_name} ({server.os_name})")
    print(f" Port: {server.port} (UDP discovery: 8766)")
    print(f" One-Time Pairing Code: {server.pairing_code}")
    print("==========================================")
    try:
        async with websockets.serve(server.handle_client, "0.0.0.0", server.port, origins=[None]):
            await asyncio.Future()
    finally:
        discovery_task.cancel()
        if zc:
            await asyncio.to_thread(zc.close)


if __name__ == "__main__":
    asyncio.run(main())
