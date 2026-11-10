package app.seguridad.imports

/*
 * Limits of «Importar de otra app» (spec «Topes comunes»). Every one is checked while reading, before the file can make
 * the app hold more: the size before any decoding, and the columns, cells, rows and field lengths inside the parser.
 */

/** Largest file read. The caller reads at most one byte more (`readAtMost(IMPORT_MAX_BYTES + 1)`), and more is refused. */
const val IMPORT_MAX_BYTES = 16 * 1024 * 1024

/** Most rows (after a CSV header) or elements in a file: above it the file is refused (decision (a), R12: the most read at once). */
const val IMPORT_MAX_ITEMS = 20_000

/** Longest field kept in an entry, in UTF-16 units (`String.length`, never fewer than the characters shown). */
const val MAX_TITLE = 1024
const val MAX_USERNAME = 1024
const val MAX_URL = 4096
const val MAX_PASSWORD = 4096
const val MAX_NOTES = 64 * 1024

/**
 * Most columns in one CSV row. The formats read have 5 to 9; the limit leaves room for columns added by other versions
 * or by a spreadsheet, and stops a line of commas from becoming millions of fields.
 */
const val CSV_MAX_COLUMNS = 64

/**
 * Most cells in a whole CSV, header included: [IMPORT_MAX_ITEMS] rows of 25 columns. With [CSV_MAX_COLUMNS] alone,
 * 20 001 rows of 64 empty cells would still be 1.3 million.
 */
const val CSV_MAX_CELLS = 500_000

/**
 * Most characters the CSV parser keeps of one field: one more than the largest field limit ([MAX_NOTES]). A longer
 * field is cut while it is read (the rest is consumed, not stored), so it is still longer than any limit and its row
 * goes to «no entran» ([SkipReason.FIELD_TOO_LONG]); a field is never imported cut.
 */
const val CSV_MAX_FIELD_CHARS = MAX_NOTES + 1
