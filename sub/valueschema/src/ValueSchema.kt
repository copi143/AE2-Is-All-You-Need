package io.github.copi143.valueschema

/**
 * Marks an immutable data class as a value-type schema.
 *
 * The KSP processor generates, next to the class:
 * - `<Name>Columns`: struct-of-arrays storage backed by one primitive array per field
 * - `<Name>View`: a zero-allocation cursor over a row of the columns
 * - inline batch operators (`forEach`, `updateAll`, `filterTo`) whose call sites read
 *   exactly like ordinary collection operations on the data class
 *
 * The annotated class must obey the value-type contract so it can later migrate to a
 * Valhalla value class by changing only the declaration:
 * - a data class with only `val` properties in the primary constructor
 * - fields of primitive types only
 * - never relied on for identity (`===`, `synchronized`, `identityHashCode`)
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class ValueSchema
