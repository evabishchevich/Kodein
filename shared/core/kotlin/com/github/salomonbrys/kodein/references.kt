package com.github.salomonbrys.kodein

import com.github.salomonbrys.kodein.bindings.Binding
import com.github.salomonbrys.kodein.bindings.BindingKodein
import com.github.salomonbrys.kodein.bindings.NoArgBinding
import com.github.salomonbrys.kodein.bindings.NoArgBindingKodein
import com.github.salomonbrys.kodein.internal.synchronizedIfNull

/**
 * A Function that creates a reference.
 */
interface RefMaker {

    /**
     * A Function that creates a reference.
     *
     * @param T The type of the referenced object.
     * @param creator A function that can create a new T.
     * @return The referenced object and a function that returns the same object if the reference is still valid.
     */
    fun <T: Any> make(creator: () -> T): Pair<T, () -> T?>
}

/**
 * Concrete referenced singleton provider: will always return the instance managed by the given reference.
 *
 * @param T The type of the instance.
 * @property refMaker Reference Maker that defines the kind of reference being used.
 * @property creator A function that should always create a new object.
 */
class RefSingletonBinding<T : Any>(override val createdType: TypeToken<T>, val refMaker: RefMaker, val creator: NoArgBindingKodein.() -> T) : NoArgBinding<T> {

     // Should always return the same object or null.
    private var _ref: (() -> T?) = { null }

    private val _lock = Any()

    override fun factoryName() = "refSingleton(${TTOf(refMaker).simpleDispString ()})"
    override fun factoryFullName() = "refSingleton(${TTOf(refMaker).fullDispString()})"

    override fun getInstance(kodein: NoArgBindingKodein, key: Kodein.Key<Unit, T>): T {
        var ret: T? = null
        synchronizedIfNull(_lock, { _ref() }, { return it }) {
            val pair = refMaker.make {
                kodein.creator()
            }
            _ref = pair.second
            ret = pair.first
        }
        return ret!!
    }
}

/**
 * Concrete referenced multiton factory: for the same argument, will always return the instance managed by the given reference.
 *
 * @param T The type of the instance.
 * @property argType The type of the argument, *used for debug print only*.
 * @property createdType The type of the created object, *used for debug print only*.
 * @property refMaker Reference Maker that defines the kind of reference being used.
 * @property creator A function that should always create a new object.
 */
class RefMultitonBinding<A, T: Any>(override val argType: TypeToken<A>, override val createdType: TypeToken<T>, val refMaker: RefMaker, val creator: BindingKodein.(A) -> T): Binding<A, T> {

    private val _refs = newConcurrentMap<A, () -> T?>()

    override fun factoryName() = "refMultiton(${TTOf(refMaker).simpleDispString()})"
    override fun factoryFullName() = "refMultiton(${TTOf(refMaker).fullDispString()})"

    override fun getInstance(kodein: BindingKodein, key: Kodein.Key<A, T>, arg: A): T {
        var ret: T? = null
        synchronizedIfNull(_refs, { _refs[arg]?.invoke() }, { return it }) {
            val pair = refMaker.make { kodein.creator(arg) }
            _refs[arg] = pair.second
            ret = pair.first
        }
        return ret!!
    }

}
