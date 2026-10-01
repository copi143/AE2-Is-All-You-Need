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
 * - fields of primitive types, or of another @ValueSchema class (nested value types are
 *   flattened into the parent storage leaf-by-leaf, exactly like Valhalla flattens
 *   value-class fields; recursive nesting is rejected because value types have no indirection)
 * - never relied on for identity (`===`, `synchronized`, `identityHashCode`)
 *
 * Nested fields are addressed by their flattened path: a field `price: Money` where
 * `Money(amount: Long, scale: Int)` produces the leaves `price_amount` / `price_scale`
 * (column names, primitive overload parameters, view properties, and transform-snippet locals).
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

/**
 * Overrides the initial value of a field for rows created by the generated `resize(newSize)`.
 *
 * KSP cannot see Kotlin constructor default values, so the fill value is given as a string
 * holding a literal of the field's type (e.g. `"-1"`, `"true"`, `"1.5"`). Fields without this
 * annotation are zero-filled (`0` / `0L` / `0.0` / `false` / `'\0'`), matching the semantics of
 * a freshly allocated primitive array. Also applies to leaf fields of nested @ValueSchema types.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class Default(val value: String)
