package aeeL.shngmWeb

/**
 * Flips the premium flag inside a response body, in place.
 *
 * `false` becomes `true ` and `!1` becomes `!0`, so JSON and minified JavaScript stay valid while
 * the buffer keeps its size. Nothing is decoded to a String and no second buffer is allocated.
 */
internal object PremiumPatcher {

  private val KEY = "is_premium".toByteArray(Charsets.US_ASCII)
  private val FALSE = "false".toByteArray(Charsets.US_ASCII)
  private val TRUE = "true ".toByteArray(Charsets.US_ASCII)
  private val FALSY = "!1".toByteArray(Charsets.US_ASCII)
  private val TRUTHY = "!0".toByteArray(Charsets.US_ASCII)

  /** Rewrites every complete flag in [body]. Returns the number of values changed. */
  fun patch(body: ByteArray): Int {
    var changed = 0
    var key = indexOf(body, 0)
    while (key >= 0) {
      val value = valueStart(body, key)
      when {
        value < 0 -> Unit

        matches(body, value, FALSE) && !isNameByte(body.getOrNull(value + FALSE.size)) -> {
          TRUE.copyInto(body, value)
          changed++
        }

        matches(body, value, FALSY) && !isNameByte(body.getOrNull(value + FALSY.size)) -> {
          TRUTHY.copyInto(body, value)
          changed++
        }
      }
      key = indexOf(body, key + KEY.size)
    }
    return changed
  }

  /** Position of the value after the key's separator, or -1 for an unrelated occurrence. */
  private fun valueStart(body: ByteArray, key: Int): Int {
    if (key > 0 && isNameByte(body[key - 1])) return -1

    var pos = key + KEY.size
    if (pos < body.size && (body[pos].toInt() == QUOTE || body[pos].toInt() == APOSTROPHE)) pos++
    while (pos < body.size && body[pos].isSpace()) pos++
    if (pos >= body.size || (body[pos].toInt() != COLON && body[pos].toInt() != EQUALS)) return -1

    pos++
    while (pos < body.size && body[pos].isSpace()) pos++
    return if (pos < body.size) pos else -1
  }

  private fun indexOf(body: ByteArray, from: Int): Int {
    var index = from
    while (index <= body.size - KEY.size) {
      if (matches(body, index, KEY)) return index
      index++
    }
    return -1
  }

  private fun matches(body: ByteArray, start: Int, expected: ByteArray): Boolean {
    if (start + expected.size > body.size) return false
    var offset = 0
    while (offset < expected.size && body[start + offset] == expected[offset]) offset++
    return offset == expected.size
  }

  private fun isNameByte(byte: Byte?): Boolean {
    val value = byte?.toInt() ?: return false
    return value >= 0x80 ||
      value in 'a'.code..'z'.code ||
      value in 'A'.code..'Z'.code ||
      value in '0'.code..'9'.code ||
      value == '_'.code ||
      value == '$'.code ||
      value == '-'.code
  }

  private fun Byte.isSpace(): Boolean =
    toInt() == ' '.code || toInt() == '\t'.code || toInt() == '\n'.code || toInt() == '\r'.code

  private const val QUOTE = '"'.code
  private const val APOSTROPHE = '\''.code
  private const val COLON = ':'.code
  private const val EQUALS = '='.code
}
