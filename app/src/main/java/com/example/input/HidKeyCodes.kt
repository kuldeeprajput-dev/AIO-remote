package com.example.input

/**
 * USB HID Usage Page 0x07 (Keyboard/Keypad) key codes and modifier bitmasks.
 */
object HidModifiers {
  const val NONE = 0
  const val LEFT_CTRL = 1 shl 0
  const val LEFT_SHIFT = 1 shl 1
  const val LEFT_ALT = 1 shl 2
  const val LEFT_GUI = 1 shl 3 // Windows / Command
  const val RIGHT_CTRL = 1 shl 4
  const val RIGHT_SHIFT = 1 shl 5
  const val RIGHT_ALT = 1 shl 6
  const val RIGHT_GUI = 1 shl 7
}

enum class HidKeyCode(val usageId: Int, val protocolName: String) {
  NONE(0x00, "NONE"),
  A(0x04, "A"), B(0x05, "B"), C(0x06, "C"), D(0x07, "D"),
  E(0x08, "E"), F(0x09, "F"), G(0x0A, "G"), H(0x0B, "H"),
  I(0x0C, "I"), J(0x0D, "J"), K(0x0E, "K"), L(0x0F, "L"),
  M(0x10, "M"), N(0x11, "N"), O(0x12, "O"), P(0x13, "P"),
  Q(0x14, "Q"), R(0x15, "R"), S(0x16, "S"), T(0x17, "T"),
  U(0x18, "U"), V(0x19, "V"), W(0x1A, "W"), X(0x1B, "X"),
  Y(0x1C, "Y"), Z(0x1D, "Z"),

  NUM_1(0x1E, "1"), NUM_2(0x1F, "2"), NUM_3(0x20, "3"), NUM_4(0x21, "4"),
  NUM_5(0x22, "5"), NUM_6(0x23, "6"), NUM_7(0x24, "7"), NUM_8(0x25, "8"),
  NUM_9(0x26, "9"), NUM_0(0x27, "0"),

  ENTER(0x28, "ENTER"),
  ESCAPE(0x29, "ESCAPE"),
  BACKSPACE(0x2A, "BACKSPACE"),
  TAB(0x2B, "TAB"),
  SPACE(0x2C, "SPACE"),
  MINUS(0x2D, "MINUS"),
  EQUAL(0x2E, "EQUAL"),
  LEFT_BRACKET(0x2F, "LEFT_BRACKET"),
  RIGHT_BRACKET(0x30, "RIGHT_BRACKET"),
  BACKSLASH(0x31, "BACKSLASH"),
  SEMICOLON(0x33, "SEMICOLON"),
  APOSTROPHE(0x34, "APOSTROPHE"),
  GRAVE(0x35, "GRAVE"),
  COMMA(0x36, "COMMA"),
  PERIOD(0x37, "PERIOD"),
  SLASH(0x38, "SLASH"),
  CAPS_LOCK(0x39, "CAPS_LOCK"),

  F1(0x3A, "F1"), F2(0x3B, "F2"), F3(0x3C, "F3"), F4(0x3D, "F4"),
  F5(0x3E, "F5"), F6(0x3F, "F6"), F7(0x40, "F7"), F8(0x41, "F8"),
  F9(0x42, "F9"), F10(0x43, "F10"), F11(0x44, "F11"), F12(0x45, "F12"),

  PRINT_SCREEN(0x46, "PRINT_SCREEN"),
  HOME(0x4A, "HOME"),
  PAGE_UP(0x4B, "PAGE_UP"),
  DELETE(0x4C, "DELETE"),
  END(0x4D, "END"),
  PAGE_DOWN(0x4E, "PAGE_DOWN"),
  RIGHT_ARROW(0x4F, "RIGHT_ARROW"),
  LEFT_ARROW(0x50, "LEFT_ARROW"),
  DOWN_ARROW(0x51, "DOWN_ARROW"),
  UP_ARROW(0x52, "UP_ARROW");

  companion object {
    fun fromProtocolName(name: String): HidKeyCode? =
      entries.firstOrNull { it.protocolName.equals(name, ignoreCase = true) }
  }
}

data class HidKeyStroke(val keyCode: HidKeyCode, val modifiers: Int = HidModifiers.NONE)

object HidAsciiMapper {
  private val charMap: Map<Char, HidKeyStroke> = mapOf(
    'a' to HidKeyStroke(HidKeyCode.A),
    'b' to HidKeyStroke(HidKeyCode.B),
    'c' to HidKeyStroke(HidKeyCode.C),
    'd' to HidKeyStroke(HidKeyCode.D),
    'e' to HidKeyStroke(HidKeyCode.E),
    'f' to HidKeyStroke(HidKeyCode.F),
    'g' to HidKeyStroke(HidKeyCode.G),
    'h' to HidKeyStroke(HidKeyCode.H),
    'i' to HidKeyStroke(HidKeyCode.I),
    'j' to HidKeyStroke(HidKeyCode.J),
    'k' to HidKeyStroke(HidKeyCode.K),
    'l' to HidKeyStroke(HidKeyCode.L),
    'm' to HidKeyStroke(HidKeyCode.M),
    'n' to HidKeyStroke(HidKeyCode.N),
    'o' to HidKeyStroke(HidKeyCode.O),
    'p' to HidKeyStroke(HidKeyCode.P),
    'q' to HidKeyStroke(HidKeyCode.Q),
    'r' to HidKeyStroke(HidKeyCode.R),
    's' to HidKeyStroke(HidKeyCode.S),
    't' to HidKeyStroke(HidKeyCode.T),
    'u' to HidKeyStroke(HidKeyCode.U),
    'v' to HidKeyStroke(HidKeyCode.V),
    'w' to HidKeyStroke(HidKeyCode.W),
    'x' to HidKeyStroke(HidKeyCode.X),
    'y' to HidKeyStroke(HidKeyCode.Y),
    'z' to HidKeyStroke(HidKeyCode.Z),
    'A' to HidKeyStroke(HidKeyCode.A, HidModifiers.LEFT_SHIFT),
    'B' to HidKeyStroke(HidKeyCode.B, HidModifiers.LEFT_SHIFT),
    'C' to HidKeyStroke(HidKeyCode.C, HidModifiers.LEFT_SHIFT),
    'D' to HidKeyStroke(HidKeyCode.D, HidModifiers.LEFT_SHIFT),
    'E' to HidKeyStroke(HidKeyCode.E, HidModifiers.LEFT_SHIFT),
    'F' to HidKeyStroke(HidKeyCode.F, HidModifiers.LEFT_SHIFT),
    'G' to HidKeyStroke(HidKeyCode.G, HidModifiers.LEFT_SHIFT),
    'H' to HidKeyStroke(HidKeyCode.H, HidModifiers.LEFT_SHIFT),
    'I' to HidKeyStroke(HidKeyCode.I, HidModifiers.LEFT_SHIFT),
    'J' to HidKeyStroke(HidKeyCode.J, HidModifiers.LEFT_SHIFT),
    'K' to HidKeyStroke(HidKeyCode.K, HidModifiers.LEFT_SHIFT),
    'L' to HidKeyStroke(HidKeyCode.L, HidModifiers.LEFT_SHIFT),
    'M' to HidKeyStroke(HidKeyCode.M, HidModifiers.LEFT_SHIFT),
    'N' to HidKeyStroke(HidKeyCode.N, HidModifiers.LEFT_SHIFT),
    'O' to HidKeyStroke(HidKeyCode.O, HidModifiers.LEFT_SHIFT),
    'P' to HidKeyStroke(HidKeyCode.P, HidModifiers.LEFT_SHIFT),
    'Q' to HidKeyStroke(HidKeyCode.Q, HidModifiers.LEFT_SHIFT),
    'R' to HidKeyStroke(HidKeyCode.R, HidModifiers.LEFT_SHIFT),
    'S' to HidKeyStroke(HidKeyCode.S, HidModifiers.LEFT_SHIFT),
    'T' to HidKeyStroke(HidKeyCode.T, HidModifiers.LEFT_SHIFT),
    'U' to HidKeyStroke(HidKeyCode.U, HidModifiers.LEFT_SHIFT),
    'V' to HidKeyStroke(HidKeyCode.V, HidModifiers.LEFT_SHIFT),
    'W' to HidKeyStroke(HidKeyCode.W, HidModifiers.LEFT_SHIFT),
    'X' to HidKeyStroke(HidKeyCode.X, HidModifiers.LEFT_SHIFT),
    'Y' to HidKeyStroke(HidKeyCode.Y, HidModifiers.LEFT_SHIFT),
    'Z' to HidKeyStroke(HidKeyCode.Z, HidModifiers.LEFT_SHIFT),
    '1' to HidKeyStroke(HidKeyCode.NUM_1),
    '!' to HidKeyStroke(HidKeyCode.NUM_1, HidModifiers.LEFT_SHIFT),
    '2' to HidKeyStroke(HidKeyCode.NUM_2),
    '@' to HidKeyStroke(HidKeyCode.NUM_2, HidModifiers.LEFT_SHIFT),
    '3' to HidKeyStroke(HidKeyCode.NUM_3),
    '#' to HidKeyStroke(HidKeyCode.NUM_3, HidModifiers.LEFT_SHIFT),
    '4' to HidKeyStroke(HidKeyCode.NUM_4),
    '$' to HidKeyStroke(HidKeyCode.NUM_4, HidModifiers.LEFT_SHIFT),
    '5' to HidKeyStroke(HidKeyCode.NUM_5),
    '%' to HidKeyStroke(HidKeyCode.NUM_5, HidModifiers.LEFT_SHIFT),
    '6' to HidKeyStroke(HidKeyCode.NUM_6),
    '^' to HidKeyStroke(HidKeyCode.NUM_6, HidModifiers.LEFT_SHIFT),
    '7' to HidKeyStroke(HidKeyCode.NUM_7),
    '&' to HidKeyStroke(HidKeyCode.NUM_7, HidModifiers.LEFT_SHIFT),
    '8' to HidKeyStroke(HidKeyCode.NUM_8),
    '*' to HidKeyStroke(HidKeyCode.NUM_8, HidModifiers.LEFT_SHIFT),
    '9' to HidKeyStroke(HidKeyCode.NUM_9),
    '(' to HidKeyStroke(HidKeyCode.NUM_9, HidModifiers.LEFT_SHIFT),
    '0' to HidKeyStroke(HidKeyCode.NUM_0),
    ')' to HidKeyStroke(HidKeyCode.NUM_0, HidModifiers.LEFT_SHIFT),
    '\n' to HidKeyStroke(HidKeyCode.ENTER),
    '\t' to HidKeyStroke(HidKeyCode.TAB),
    ' ' to HidKeyStroke(HidKeyCode.SPACE),
    '-' to HidKeyStroke(HidKeyCode.MINUS),
    '_' to HidKeyStroke(HidKeyCode.MINUS, HidModifiers.LEFT_SHIFT),
    '=' to HidKeyStroke(HidKeyCode.EQUAL),
    '+' to HidKeyStroke(HidKeyCode.EQUAL, HidModifiers.LEFT_SHIFT),
    '[' to HidKeyStroke(HidKeyCode.LEFT_BRACKET),
    '{' to HidKeyStroke(HidKeyCode.LEFT_BRACKET, HidModifiers.LEFT_SHIFT),
    ']' to HidKeyStroke(HidKeyCode.RIGHT_BRACKET),
    '}' to HidKeyStroke(HidKeyCode.RIGHT_BRACKET, HidModifiers.LEFT_SHIFT),
    '\\' to HidKeyStroke(HidKeyCode.BACKSLASH),
    '|' to HidKeyStroke(HidKeyCode.BACKSLASH, HidModifiers.LEFT_SHIFT),
    ';' to HidKeyStroke(HidKeyCode.SEMICOLON),
    ':' to HidKeyStroke(HidKeyCode.SEMICOLON, HidModifiers.LEFT_SHIFT),
    '\'' to HidKeyStroke(HidKeyCode.APOSTROPHE),
    '"' to HidKeyStroke(HidKeyCode.APOSTROPHE, HidModifiers.LEFT_SHIFT),
    '`' to HidKeyStroke(HidKeyCode.GRAVE),
    '~' to HidKeyStroke(HidKeyCode.GRAVE, HidModifiers.LEFT_SHIFT),
    ',' to HidKeyStroke(HidKeyCode.COMMA),
    '<' to HidKeyStroke(HidKeyCode.COMMA, HidModifiers.LEFT_SHIFT),
    '.' to HidKeyStroke(HidKeyCode.PERIOD),
    '>' to HidKeyStroke(HidKeyCode.PERIOD, HidModifiers.LEFT_SHIFT),
    '/' to HidKeyStroke(HidKeyCode.SLASH),
    '?' to HidKeyStroke(HidKeyCode.SLASH, HidModifiers.LEFT_SHIFT)
  )

  fun mapChar(ch: Char): HidKeyStroke? = charMap[ch]

  fun supportsString(text: String): Boolean = text.all { charMap.containsKey(it) }
}
