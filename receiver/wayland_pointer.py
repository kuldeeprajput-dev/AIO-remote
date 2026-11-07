"""Pointer input through the desktop's consent-based Remote Desktop portal."""

import asyncio
import uuid

from dbus_next import Message, MessageType, Variant
from dbus_next.aio import MessageBus

PORTAL = "org.freedesktop.portal.Desktop"
DESKTOP = "/org/freedesktop/portal/desktop"
REMOTE_DESKTOP = "org.freedesktop.portal.RemoteDesktop"
REQUEST = "org.freedesktop.portal.Request"
SESSION = "org.freedesktop.portal.Session"
POINTER = 2
BUTTONS = {"LEFT": 272, "RIGHT": 273, "MIDDLE": 274}


class WaylandPointer:
    def __init__(self):
        self.bus = None
        self.session = None
        self.held_buttons = set()

    async def _call(self, path, interface, member, signature="", body=None):
        reply = await self.bus.call(Message(
            destination=PORTAL, path=path, interface=interface, member=member,
            signature=signature, body=body or [],
        ))
        if reply.message_type == MessageType.ERROR:
            raise RuntimeError(reply.body[0] if reply.body else reply.error_name)
        return reply.body

    async def _request(self, member, signature, args, options):
        token = "slate_" + uuid.uuid4().hex
        sender = self.bus.unique_name[1:].replace(".", "_")
        path = f"{DESKTOP}/request/{sender}/{token}"
        response = asyncio.get_running_loop().create_future()

        def handle_response(message):
            if (message.message_type == MessageType.SIGNAL and message.path == path
                    and message.interface == REQUEST and message.member == "Response"
                    and not response.done()):
                response.set_result(message.body)

        self.bus.add_message_handler(handle_response)
        try:
            await self._call(DESKTOP, REMOTE_DESKTOP, member, signature, [
                *args, {**options, "handle_token": Variant("s", token)}
            ])
            code, result = await response
            if code:
                raise PermissionError("Allow Remote Desktop control on the laptop to use the Wi-Fi touchpad.")
            return result
        finally:
            self.bus.remove_message_handler(handle_response)
            if not response.done() or response.cancelled():
                try:
                    await self._call(path, REQUEST, "Close")
                except RuntimeError:
                    pass

    async def start(self):
        self.bus = await MessageBus().connect()
        match = "type='signal',sender='org.freedesktop.portal.Desktop',interface='org.freedesktop.portal.Request',member='Response'"
        reply = await self.bus.call(Message(
            destination="org.freedesktop.DBus", path="/org/freedesktop/DBus",
            interface="org.freedesktop.DBus", member="AddMatch", signature="s", body=[match],
        ))
        if reply.message_type == MessageType.ERROR:
            raise RuntimeError("Could not listen for desktop permission.")
        created = await self._request("CreateSession", "a{sv}", [], {
            "session_handle_token": Variant("s", "slate_" + uuid.uuid4().hex),
        })
        self.session = created["session_handle"].value
        await self._request("SelectDevices", "oa{sv}", [self.session], {"types": Variant("u", POINTER)})
        granted = await self._request("Start", "osa{sv}", [self.session, ""], {})
        if not granted.get("devices", Variant("u", 0)).value & POINTER:
            raise PermissionError("The desktop did not grant pointer control.")

    async def _notify(self, method, signature, *args, options=None):
        await self._call(DESKTOP, REMOTE_DESKTOP, method, "oa{sv}" + signature, [
            self.session, options or {}, *args
        ])

    async def _button(self, button, pressed):
        await self._notify("NotifyPointerButton", "iu", button, int(pressed))
        if pressed:
            self.held_buttons.add(button)
        else:
            self.held_buttons.discard(button)

    async def dispatch(self, data):
        action = data.get("type")
        if action == "RELEASE_ALL":
            for button in tuple(self.held_buttons):
                await self._button(button, False)
        elif action == "POINTER_MOVE":
            await self._notify("NotifyPointerMotion", "dd", float(data.get("dx", 0)), float(data.get("dy", 0)))
        elif action == "POINTER_SCROLL":
            for axis, key in enumerate(("dy", "dx")):
                steps = int(data.get(key, 0))
                if steps:
                    # Slate follows HID wheel direction, where positive means up.
                    await self._notify("NotifyPointerAxisDiscrete", "ui", axis, -steps if axis == 0 else steps)
        else:
            button = BUTTONS.get(data.get("button", "LEFT"))
            if button is None:
                return
            if action == "POINTER_CLICK":
                for _ in range(max(1, min(3, int(data.get("count", 1))))):
                    await self._button(button, True)
                    await self._button(button, False)
            else:
                await self._button(button, action == "POINTER_DOWN")

    async def close(self):
        if self.bus:
            try:
                if self.session:
                    await self._call(self.session, SESSION, "Close")
            except RuntimeError:
                pass
            finally:
                self.bus.disconnect()
                self.bus = None
                self.session = None
