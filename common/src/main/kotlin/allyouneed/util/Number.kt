@file:Suppress("SpellCheckingInspection", "unused")

package allyouneed.util

import java.math.BigInteger

data class Number(val bigInt: BigInteger) {
    val intOverflow = bigInt.bitLength() > 31
    val longOverflow = bigInt.bitLength() > 63
    val int = bigInt.toInt()
    val long = bigInt.toLong()
    val float = bigInt.toFloat()
    val double = bigInt.toDouble()

    companion object {
        @JvmStatic
        fun powOfTwo(e: Int): Number {
            return Number(BigInteger.TWO.pow(e))
        }

        @JvmStatic
        fun powOfTen(e: Int): Number {
            return Number(BigInteger.TEN.pow(e))
        }
    }
}

val kilo = Number.powOfTen(3)
val mega = Number.powOfTen(6)
val giga = Number.powOfTen(9)
val tera = Number.powOfTen(12)
val peta = Number.powOfTen(15)
val exa = Number.powOfTen(18)
val zetta = Number.powOfTen(21)
val yotta = Number.powOfTen(24)
val ronna = Number.powOfTen(27)
val quetta = Number.powOfTen(30)

val kibi = Number.powOfTwo(10)
val mebi = Number.powOfTwo(20)
val gibi = Number.powOfTwo(30)
val tebi = Number.powOfTwo(40)
val pebi = Number.powOfTwo(50)
val exbi = Number.powOfTwo(60)
val zebi = Number.powOfTwo(70)
val yobi = Number.powOfTwo(80)
val robi = Number.powOfTwo(90)
val quebi = Number.powOfTwo(100)

val BigInteger.Ki get() = this * kibi.bigInt
val BigInteger.Mi get() = this * mebi.bigInt
val BigInteger.Gi get() = this * gibi.bigInt
val BigInteger.Ti get() = this * tebi.bigInt
val BigInteger.Pi get() = this * pebi.bigInt
val BigInteger.Ei get() = this * exbi.bigInt
val BigInteger.Zi get() = this * zebi.bigInt
val BigInteger.Yi get() = this * yobi.bigInt
val BigInteger.Ri get() = this * robi.bigInt
val BigInteger.Qi get() = this * quebi.bigInt

val Int.Ki get() = this * kibi.int
val Int.Mi get() = this * mebi.int
val Int.Gi get() = this * gibi.int

val Long.Ki get() = this * kibi.long
val Long.Mi get() = this * mebi.long
val Long.Gi get() = this * gibi.long
val Long.Ti get() = this * tebi.long
val Long.Pi get() = this * pebi.long
val Long.Ei get() = this * exbi.long

val Float.Ki get() = this * kibi.float
val Float.Mi get() = this * mebi.float
val Float.Gi get() = this * gibi.float
val Float.Ti get() = this * tebi.float
val Float.Pi get() = this * pebi.float
val Float.Ei get() = this * exbi.float
val Float.Zi get() = this * zebi.float
val Float.Yi get() = this * yobi.float
val Float.Ri get() = this * robi.float
val Float.Qi get() = this * quebi.float

val Double.Ki get() = this * kibi.double
val Double.Mi get() = this * mebi.double
val Double.Gi get() = this * gibi.double
val Double.Ti get() = this * tebi.double
val Double.Pi get() = this * pebi.double
val Double.Ei get() = this * exbi.double
val Double.Zi get() = this * zebi.double
val Double.Yi get() = this * yobi.double
val Double.Ri get() = this * robi.double
val Double.Qi get() = this * quebi.double
