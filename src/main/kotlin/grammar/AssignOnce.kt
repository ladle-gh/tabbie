package grammar

import kotlin.reflect.KProperty

class AssignOnce<T : Any> {
    private var value: T? = null

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value ?: throw IllegalCallerException("Property has not been initialized yet")
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value?.let { throw IllegalCallerException("Property has already been initialized") }
        this.value = value
    }
}