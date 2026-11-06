package com.example.input

/**
 * Standard USB HID Report Descriptor combining:
 * - Report ID 1: 3-button Mouse with 8-bit X/Y deltas, Vertical Wheel, and Horizontal AC Pan
 * - Report ID 2: Standard 104-key Boot-compatible Keyboard (1 modifier byte, 1 reserved, 6 keycodes)
 * - Report ID 3: Consumer Control (Media Play/Pause, Next, Prev, Stop, Vol+, Vol-, Mute, FF, Rewind)
 */
object HidDescriptors {
  const val REPORT_ID_MOUSE: Int = 0x01
  const val REPORT_ID_KEYBOARD: Int = 0x02
  const val REPORT_ID_CONSUMER: Int = 0x03

  val COMPOSITE_DESCRIPTOR: ByteArray = byteArrayOf(
    // ===== Mouse (Report ID 1) =====
    0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
    0x09.toByte(), 0x02.toByte(), // Usage (Mouse)
    0xA1.toByte(), 0x01.toByte(), // Collection (Application)
    0x85.toByte(), 0x01.toByte(), //   Report ID (1)
    0x09.toByte(), 0x01.toByte(), //   Usage (Pointer)
    0xA1.toByte(), 0x00.toByte(), //   Collection (Physical)
    0x05.toByte(), 0x09.toByte(), //     Usage Page (Button)
    0x19.toByte(), 0x01.toByte(), //     Usage Minimum (Button 1)
    0x29.toByte(), 0x03.toByte(), //     Usage Maximum (Button 3)
    0x15.toByte(), 0x00.toByte(), //     Logical Minimum (0)
    0x25.toByte(), 0x01.toByte(), //     Logical Maximum (1)
    0x95.toByte(), 0x03.toByte(), //     Report Count (3)
    0x75.toByte(), 0x01.toByte(), //     Report Size (1)
    0x81.toByte(), 0x02.toByte(), //     Input (Data, Variable, Absolute)
    0x95.toByte(), 0x01.toByte(), //     Report Count (1)
    0x75.toByte(), 0x05.toByte(), //     Report Size (5) - Padding
    0x81.toByte(), 0x03.toByte(), //     Input (Constant)
    0x05.toByte(), 0x01.toByte(), //     Usage Page (Generic Desktop)
    0x09.toByte(), 0x30.toByte(), //     Usage (X)
    0x09.toByte(), 0x31.toByte(), //     Usage (Y)
    0x09.toByte(), 0x38.toByte(), //     Usage (Wheel)
    0x15.toByte(), 0x81.toByte(), //     Logical Minimum (-127)
    0x25.toByte(), 0x7F.toByte(), //     Logical Maximum (127)
    0x75.toByte(), 0x08.toByte(), //     Report Size (8)
    0x95.toByte(), 0x03.toByte(), //     Report Count (3)
    0x81.toByte(), 0x06.toByte(), //     Input (Data, Variable, Relative)
    0x05.toByte(), 0x0C.toByte(), //     Usage Page (Consumer)
    0x0A.toByte(), 0x38.toByte(), 0x02.toByte(), // Usage (AC Pan - horizontal scroll)
    0x15.toByte(), 0x81.toByte(), //     Logical Minimum (-127)
    0x25.toByte(), 0x7F.toByte(), //     Logical Maximum (127)
    0x75.toByte(), 0x08.toByte(), //     Report Size (8)
    0x95.toByte(), 0x01.toByte(), //     Report Count (1)
    0x81.toByte(), 0x06.toByte(), //     Input (Data, Variable, Relative)
    0xC0.toByte(),                //   End Collection (Physical)
    0xC0.toByte(),                // End Collection (Application)

    // ===== Keyboard (Report ID 2) =====
    0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
    0x09.toByte(), 0x06.toByte(), // Usage (Keyboard)
    0xA1.toByte(), 0x01.toByte(), // Collection (Application)
    0x85.toByte(), 0x02.toByte(), //   Report ID (2)
    0x05.toByte(), 0x07.toByte(), //   Usage Page (Keyboard/Keypad)
    0x19.toByte(), 0xE0.toByte(), //   Usage Minimum (Left Control)
    0x29.toByte(), 0xE7.toByte(), //   Usage Maximum (Right GUI)
    0x15.toByte(), 0x00.toByte(), //   Logical Minimum (0)
    0x25.toByte(), 0x01.toByte(), //   Logical Maximum (1)
    0x75.toByte(), 0x01.toByte(), //   Report Size (1)
    0x95.toByte(), 0x08.toByte(), //   Report Count (8)
    0x81.toByte(), 0x02.toByte(), //   Input (Data, Variable, Absolute) - Modifier byte
    0x95.toByte(), 0x01.toByte(), //   Report Count (1)
    0x75.toByte(), 0x08.toByte(), //   Report Size (8)
    0x81.toByte(), 0x03.toByte(), //   Input (Constant) - Reserved byte
    0x95.toByte(), 0x06.toByte(), //   Report Count (6)
    0x75.toByte(), 0x08.toByte(), //   Report Size (8)
    0x15.toByte(), 0x00.toByte(), //   Logical Minimum (0)
    0x25.toByte(), 0x65.toByte(), //   Logical Maximum (101)
    0x05.toByte(), 0x07.toByte(), //   Usage Page (Keyboard/Keypad)
    0x19.toByte(), 0x00.toByte(), //   Usage Minimum (0)
    0x29.toByte(), 0x65.toByte(), //   Usage Maximum (101)
    0x81.toByte(), 0x00.toByte(), //   Input (Data, Array) - 6 keycodes
    0xC0.toByte(),                // End Collection

    // ===== Consumer Control (Report ID 3) =====
    0x05.toByte(), 0x0C.toByte(), // Usage Page (Consumer)
    0x09.toByte(), 0x01.toByte(), // Usage (Consumer Control)
    0xA1.toByte(), 0x01.toByte(), // Collection (Application)
    0x85.toByte(), 0x03.toByte(), //   Report ID (3)
    0x15.toByte(), 0x00.toByte(), //   Logical Minimum (0)
    0x25.toByte(), 0x01.toByte(), //   Logical Maximum (1)
    0x75.toByte(), 0x01.toByte(), //   Report Size (1)
    0x95.toByte(), 0x09.toByte(), //   Report Count (9)
    0x09.toByte(), 0xCD.toByte(), //   Bit 0: Play/Pause (0xCD)
    0x09.toByte(), 0xB5.toByte(), //   Bit 1: Scan Next Track (0xB5)
    0x09.toByte(), 0xB6.toByte(), //   Bit 2: Scan Previous Track (0xB6)
    0x09.toByte(), 0xB7.toByte(), //   Bit 3: Stop (0xB7)
    0x09.toByte(), 0xE9.toByte(), //   Bit 4: Volume Increment (0xE9)
    0x09.toByte(), 0xEA.toByte(), //   Bit 5: Volume Decrement (0xEA)
    0x09.toByte(), 0xE2.toByte(), //   Bit 6: Mute (0xE2)
    0x09.toByte(), 0xB3.toByte(), //   Bit 7: Fast Forward (0xB3)
    0x09.toByte(), 0xB4.toByte(), //   Bit 8: Rewind (0xB4)
    0x81.toByte(), 0x02.toByte(), //   Input (Data, Variable, Absolute)
    0x75.toByte(), 0x07.toByte(), //   Report Size (7) - Padding to byte boundary
    0x95.toByte(), 0x01.toByte(), //   Report Count (1)
    0x81.toByte(), 0x03.toByte(), //   Input (Constant)
    0xC0.toByte()                 // End Collection
  )
}
