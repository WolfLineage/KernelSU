package com.vorthas.yusu.ui.screen.terminal

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Minimal VT100/ANSI emulator: enough for a real shell, vi/nano, htop and coloured prompt.
 * It keeps a character grid and notifies the UI after each write so Compose can render
 * `AnnotatedString` rows with SGR colours and attributes.
 */
data class TerminalCell(
    val char: Char = ' ',
    val fg: Int? = null,
    val bg: Int? = null,
    val bold: Boolean = false,
    val reverse: Boolean = false,
)

data class TerminalFrame(
    val rows: List<List<TerminalCell>>,
    val cursorRow: Int,
    val cursorCol: Int,
    val columns: Int,
)

private const val MAX_ROWS = 4000

private val ANSI_COLORS = intArrayOf(
    0xFF1C1C1C.toInt(), 0xFFE06C75.toInt(), 0xFF98C379.toInt(), 0xFFE5C07B.toInt(),
    0xFF61AFEF.toInt(), 0xFFC678DD.toInt(), 0xFF56B6C2.toInt(), 0xFFABB2BF.toInt(),
    0xFF5C6370.toInt(), 0xFFF07178.toInt(), 0xFFB5CEA8.toInt(), 0xFFD19A66.toInt(),
    0xFF61AFEF.toInt(), 0xFFC678DD.toInt(), 0xFF56B6C2.toInt(), 0xFFFFFFFF.toInt(),
)

class TerminalEmulator(
    columns: Int = 80,
    private val onChange: () -> Unit,
) {
    private var cols = columns.coerceAtLeast(20)
    private var grid = mutableListOf<MutableList<TerminalCell>>()
    private var row = 0
    private var col = 0
    private var fg: Int? = null
    private var bg: Int? = null
    private var bold = false
    private var reverse = false
    private var savedRow = 0
    private var savedCol = 0
    private var state = State.NORMAL
    private var params = StringBuilder()
    private var osc = StringBuilder()
    // Streaming decoder keeps incomplete UTF-8 sequences across PTY reads. Previously every
    // byte was treated as a Latin-1 character, which garbled Chinese output and scripts.
    private val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)

    private enum class State { NORMAL, ESC, CSI, OSC, CHARSET }

    init {
        grid.add(blankRow())
    }

    @Synchronized
    fun frame(): TerminalFrame = TerminalFrame(grid.map { it.toList() }, row, col, cols)

    @Synchronized
    fun resize(columns: Int) {
        val newCols = columns.coerceAtLeast(20)
        if (newCols == cols) return
        cols = newCols
        grid.forEach { cells ->
            while (cells.size < cols) cells.add(TerminalCell())
            if (cells.size > cols) cells.subList(cols, cells.size).clear()
        }
        col = col.coerceAtMost(cols - 1)
        onChange()
    }

    @Synchronized
    fun write(bytes: ByteArray) {
        val out = CharBuffer.allocate(bytes.size + 8)
        decoder.decode(ByteBuffer.wrap(bytes), out, false)
        out.flip()
        val chars = CharArray(out.remaining())
        out.get(chars)
        for (code in chars) when (state) {
            State.NORMAL -> normal(code.code)
            State.ESC -> escape(code.code)
            State.CSI -> csi(code.code)
            State.OSC -> osc(code.code)
            State.CHARSET -> state = State.NORMAL
        }
        onChange()
    }

    private fun blankRow(): MutableList<TerminalCell> = MutableList(cols) { TerminalCell() }

    private fun ensureRow(index: Int) {
        while (grid.size <= index && grid.size < MAX_ROWS) grid.add(blankRow())
    }

    private fun scrollIfNeeded() {
        while (row >= grid.size && grid.size < MAX_ROWS) grid.add(blankRow())
        if (row >= MAX_ROWS) {
            grid.removeAt(0)
            row = MAX_ROWS - 1
        }
    }

    private fun normal(byte: Int) {
        when (byte) {
            0x07 -> Unit // bell
            0x08 -> col = (col - 1).coerceAtLeast(0)
            0x09 -> col = minOf(((col / 8) + 1) * 8, cols - 1)
            0x0A -> { // LF
                row++
                scrollIfNeeded()
            }
            0x0D -> col = 0 // CR
            0x1B -> state = State.ESC
            else -> {
                if (byte < 0x20) return
                if (col >= cols) {
                    col = 0
                    row++
                    scrollIfNeeded()
                }
                ensureRow(row)
                grid[row][col] = TerminalCell(byte.toChar(), fg, bg, bold, reverse)
                col++
            }
        }
    }

    private fun escape(byte: Int) {
        when (byte) {
            '['.code -> { params.setLength(0); state = State.CSI }
            ']'.code -> { osc.setLength(0); state = State.OSC }
            '(' .code, ')'.code, '#'.code -> state = State.CHARSET
            'D'.code -> { row++; scrollIfNeeded(); state = State.NORMAL }
            'E'.code -> { row++; col = 0; scrollIfNeeded(); state = State.NORMAL }
            'M'.code -> { row--; if (row < 0) row = 0; state = State.NORMAL }
            'c'.code -> reset()
            else -> state = State.NORMAL
        }
    }

    private fun osc(byte: Int) {
        when (byte) {
            0x07 -> state = State.NORMAL
            0x1B -> Unit // next byte is '\\'
            '\\'.code -> state = State.NORMAL
            else -> osc.append(byte.toChar())
        }
    }

    private fun csi(byte: Int) {
        if (byte in 0x30..0x3F || byte == ';'.code || byte == '?'.code || byte == '>'.code || byte == ':'.code) {
            params.append(byte.toChar())
            return
        }
        state = State.NORMAL
        val raw = params.toString().trimStart('?', '>')
        val values = raw.split(';').map { it.toIntOrNull() ?: 0 }
        val first = values.firstOrNull() ?: 0
        when (byte) {
            'A'.code -> row = (row - maxOf(1, first)).coerceAtLeast(0)
            'B'.code -> { row += maxOf(1, first); scrollIfNeeded() }
            'C'.code -> col = minOf(col + maxOf(1, first), cols - 1)
            'D'.code -> col = (col - maxOf(1, first)).coerceAtLeast(0)
            'E'.code -> {
                row += maxOf(1, first); scrollIfNeeded(); col = 0
            }
            'F'.code -> {
                row = (row - maxOf(1, first)).coerceAtLeast(0); col = 0
            }
            'G'.code, '`'.code -> col = (maxOf(1, first) - 1).coerceIn(0, cols - 1)
            'H'.code, 'f'.code -> {
                val targetRow = (values.getOrNull(0).takeIf { it != null && it > 0 } ?: 1) - 1
                val targetCol = (values.getOrNull(1).takeIf { it != null && it > 0 } ?: 1) - 1
                row = targetRow.coerceAtLeast(0)
                col = targetCol.coerceIn(0, cols - 1)
                ensureRow(row)
                scrollIfNeeded()
            }
            'J'.code -> eraseDisplay(first)
            'K'.code -> eraseLine(first)
            'L'.code -> {
                repeat(maxOf(1, first)) { grid.add(row, blankRow()) }
            }
            'M'.code -> {
                repeat(minOf(maxOf(1, first), grid.size)) {
                    if (grid.size > 1) grid.removeAt(row.coerceAtMost(grid.size - 1))
                }
            }
            'S'.code -> repeat(maxOf(1, first)) { grid.removeAt(0); grid.add(blankRow()) }
            'T'.code -> repeat(maxOf(1, first)) { if (grid.size > 1) { grid.removeAt(grid.lastIndex); grid.add(0, blankRow()) } }
            'm'.code -> applySgr(values)
            's'.code -> { savedRow = row; savedCol = col }
            'u'.code -> { row = savedRow; col = savedCol }
        }
    }

    private fun eraseDisplay(mode: Int) {
        ensureRow(row)
        when (mode) {
            0 -> {
                fill(row, col, cols, TerminalCell())
                for (r in row + 1 until grid.size) fill(r, 0, cols, TerminalCell())
            }
            1 -> {
                fill(row, 0, col + 1, TerminalCell())
                for (r in 0 until row) fill(r, 0, cols, TerminalCell())
            }
            2, 3 -> grid.forEach { fillRow(it, TerminalCell()) }
        }
    }

    private fun eraseLine(mode: Int) {
        ensureRow(row)
        when (mode) {
            0 -> fill(row, col, cols, TerminalCell())
            1 -> fill(row, 0, col + 1, TerminalCell())
            2 -> fillRow(grid[row], TerminalCell())
        }
    }

    private fun fill(r: Int, from: Int, to: Int, cell: TerminalCell) {
        if (r !in grid.indices) return
        for (c in from.coerceAtLeast(0) until to.coerceAtMost(cols)) grid[r][c] = cell
    }

    private fun fillRow(cells: MutableList<TerminalCell>, cell: TerminalCell) {
        for (c in 0 until cols) cells[c] = cell
    }

    private fun applySgr(values: List<Int>) {
        if (values.isEmpty()) {
            resetAttributes()
            return
        }
        var i = 0
        while (i < values.size) {
            when (val code = values[i]) {
                0 -> resetAttributes()
                1 -> bold = true
                22 -> bold = false
                7 -> reverse = true
                27 -> reverse = false
                39 -> fg = null
                49 -> bg = null
                in 30..37 -> fg = ANSI_COLORS[code - 30]
                in 90..97 -> fg = ANSI_COLORS[code - 90 + 8]
                in 40..47 -> bg = ANSI_COLORS[code - 40]
                in 100..107 -> bg = ANSI_COLORS[code - 100 + 8]
                38, 48 -> {
                    if (values.getOrNull(i + 1) == 5) {
                        val palette = values.getOrNull(i + 2)?.coerceIn(0, 255)
                        if (palette != null) {
                            val color = if (palette < 16) ANSI_COLORS[palette]
                            else {
                                val level = if (palette < 232) palette - 16 else 232 + (palette - 232) * 10
                                val r = if (palette < 232) (level / 36) * 51 else level
                                val g = if (palette < 232) ((level / 6) % 6) * 51 else level
                                val b = if (palette < 232) (level % 6) * 51 else level
                                (0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()).toInt()
                            }
                            if (code == 38) fg = color else bg = color
                        }
                        i += 2
                    } else if (values.getOrNull(i + 1) == 2) {
                        val r = values.getOrNull(i + 2)?.coerceIn(0, 255) ?: 0
                        val g = values.getOrNull(i + 3)?.coerceIn(0, 255) ?: 0
                        val b = values.getOrNull(i + 4)?.coerceIn(0, 255) ?: 0
                        val color = (0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()).toInt()
                        if (code == 38) fg = color else bg = color
                        i += 4
                    }
                }
            }
            i++
        }
    }

    private fun resetAttributes() {
        fg = null
        bg = null
        bold = false
        reverse = false
    }

    private fun reset() {
        resetAttributes()
        row = 0
        col = 0
        grid.clear()
        grid.add(blankRow())
    }
}
