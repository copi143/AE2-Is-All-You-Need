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
annotation class ValueSchema(val transforms: Array<ValueTransform> = [])

/**
 * Declares a field-wise transform to be generated on the Columns/Packed storage.
 *
 * [body] is a Kotlin code snippet pasted into the generated function and compiled by kotlinc
 * (so it is type-checked like ordinary code). Inside the snippet, every field of the schema is
 * in scope as a mutable local variable; after the snippet runs, all fields are written back to
 * storage. The generated function is unconditionally allocation-free: no value instance is
 * ever constructed.
 *
 * Constraints: the snippet may only touch field-name locals, [params], and pure Kotlin
 * (no `this`, no `copy`, no member calls on the value). [params] is a simple comma-separated
 * list of `name: Type` declarations.
 */
@Retention(AnnotationRetention.SOURCE)
annotation class ValueTransform(val name: String, val params: String = "", val body: String)
