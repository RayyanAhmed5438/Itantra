package com.tactical.domain.result

/**
 * Generic Success/Failure wrapper for cross-module calls that may fail,
 * used instead of throwing exceptions across module boundaries.
 *
 * NOTE: core.md's own file spec calls this a "sealed class," but its
 * Implementation Guidelines (rule 6) list TacticalResult as an example of
 * "prefer sealed interfaces for closed sets," alongside Packet, ModelStatus,
 * and DomainEvent. Built as sealed interface here for consistency with
 * every other closed-set type in this module and with the guideline —
 * worth flagging the contradiction to whoever maintains core.md.
 */
sealed interface TacticalResult<out T> {

    data class Success<out T>(val value: T) : TacticalResult<T>

    data class Failure(val error: String) : TacticalResult<Nothing>

    /** Returns the success value, or null if this is a Failure. */
    fun getOrNull(): T? = (this as? Success<T>)?.value

    /** Transforms the success value if present; passes a Failure through
     *  unchanged. */
    fun <R> map(transform: (T) -> R): TacticalResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }
}