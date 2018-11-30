package com.typesafe.config.impl
import scala.annotation.tailrec

/**
 * A terrible Map that isn't as expensive as HashMap to copy and
 * add one item to. Please write something real if you see this
 * and get cranky.
 */
object BadMap {
    final private[impl] class Entry private[impl] (val hash: Int, val key: Any, val value: Any, val next: BadMap.Entry) {
        @tailrec
        private[impl] def find(k: Any): Any =
            if (key == k) value
            else if (next != null) next.find(k)
            else null
    }
    private[impl] val emptyEntries = Array[Entry]()
    private def store[K, V](entries: Array[Entry], hash: Int, k: K, v: V): Unit = {
        val i = hash % entries.length
        val old = entries(i) // old may be null
        entries(i) = new Entry(hash, k, v, old)
    }
    private def store(entries: Array[Entry], e: Entry): Unit = {
        val i = e.hash % entries.length
        val old = entries(i)
        if (old == null && e.next == null) { // share the entry since it has no "next"
            entries(i) = e
        } else { // bah, have to copy it
            entries(i) = new Entry(e.hash, e.key, e.value, old)
        }
    }
    private def rehash(src: Array[Entry], dest: Array[BadMap.Entry]): Unit = {
        //        for (entry <- src) {
        // have to store each "next" element individually; they may belong in different indices
        //            while ({ entry != null }) {
        //                store(dest, entry)
        //                entry = entry.next
        //            }
        //        }
        @tailrec
        def processEntry(entry: Entry): Unit = {
            if (entry != null) {
                store(dest, entry)
                processEntry(entry.next)
            }
        }

        for (entry <- src) {
            processEntry(entry)
        }
    }
    private val primes = Array(
        /* Skip some early ones that are close together */
        2, /* 3, */ 5, /* 7, */ 11, /* 13, */ 17, /* 19, */ 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71,
        73, 79, 83, 89, 97, 101, 103, 107, 109, 113, 127, 131, 137, 139, 149, 151, 157, 163, 167, 173,
        179, 181, 191, 193, 197, 199, 211, 223, 227, 229, 233, 239, 241, 251, 257, 263, 269, 271, 277, 281,
        283, 293, 307, 311, 313, 317, 331, 337, 347, 349, 353, 359, 367, 373, 379, 383, 389, 397, 401, 409,
        419, 421, 431, 433, 439, 443, 449, 457, 461, 463, 467, 479, 487, 491, 499, 503, 509, 521, 523, 541,
        547, 557, 563, 569, 571, 577, 587, 593, 599, 601, 607, 613, 617, 619, 631, 641, 643, 647, 653, 659,
        661, 673, 677, 683, 691, 701, 709, 719, 727, 733, 739, 743, 751, 757, 761, 769, 773, 787, 797, 809,
        811, 821, 823, 827, 829, 839, 853, 857, 859, 863, 877, 881, 883, 887, 907, 911, 919, 929, 937, 941,
        947, 953, 967, 971, 977, 983, 991, 997, 1009,
        /* now we start skipping some, this is arbitrary */
        2053, 3079, 4057, 7103, 10949, 16069, 32609, 65867, 104729)

    private def nextPrime(i: Int): Int = {
        for (p <- primes) {
            if (p > i) return p
        }
        /* oh well */ primes(primes.length - 1)
    }
}

final class BadMap[K, V] private (val size: Int, val entries: Array[BadMap.Entry]) {
    def this() {
        this(0, BadMap.emptyEntries)
    }
    private[impl] def copyingPut(k: K, v: V) = {
        val newSize = size + 1
        var newEntries: Array[BadMap.Entry] = null
        if (newSize > entries.length) { // nextPrime doesn't always return a prime larger than
            // we passed in, so this block may not actually change
            // the entries size. the "-1" is to ensure we use
            // array length 2 when going from 0 to 1.
            newEntries = new Array[BadMap.Entry](BadMap.nextPrime((newSize * 2) - 1))
        } else newEntries = new Array[BadMap.Entry](entries.length)
        if (newEntries.length == entries.length)
            System.arraycopy(entries, 0, newEntries, 0, entries.length)
        else BadMap.rehash(entries, newEntries)
        val hash = Math.abs(k.hashCode)
        BadMap.store(newEntries, hash, k, v)
        new BadMap[K, V](newSize, newEntries)
    }
    @SuppressWarnings(Array("unchecked")) private[impl] def get(k: K): V =
        if (entries.length == 0) null.asInstanceOf[V]
        else {
            val hash = Math.abs(k.hashCode)
            val i = hash % entries.length
            val e = entries(i)
            if (e == null) null.asInstanceOf[V]
            else e.find(k).asInstanceOf[V]
        }
}
