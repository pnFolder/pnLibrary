package org.pnfolder.pnlibrary.api.currency

import java.math.BigDecimal

/**
 * Validated intent submitted to [CurrencyStorage.transact].
 *
 * Storage implementations validate the account combination required by [type]. A
 * non-null [idempotencyKey] identifies one logical request within a currency: retries
 * return the already persisted transaction instead of applying the balance change twice.
 *
 * @property type requested balance mutation
 * @property amount amount interpreted according to the currency descriptor
 * @property source debited account for debit and transfer operations
 * @property target credited or replaced account for credit, transfer, set, and reset
 * @property actor principal responsible for the request
 * @property service application operation or subsystem producing the request
 * @property reason optional human-readable audit reason, limited to 512 characters
 * @property metadata structured audit values, limited to 64 entries
 * @property idempotencyKey optional retry identity, nonblank and at most 128 characters
 */
data class CurrencyTransactionRequest(
    val type: CurrencyTransactionType,
    val amount: BigDecimal,
    val source: CurrencyAccount? = null,
    val target: CurrencyAccount? = null,
    val actor: CurrencyActor,
    val service: String,
    val reason: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val idempotencyKey: String? = null,
) {
    init {
        require(service.isNotBlank()) { "Currency transaction service must not be blank" }
        require(metadata.size <= 64) { "Currency transaction metadata cannot contain more than 64 entries" }
        require(service.length <= 128) { "Currency transaction service is too long" }
        require(reason == null || reason.length <= 512) { "Currency transaction reason is too long" }
        require(idempotencyKey == null || idempotencyKey.isNotBlank() && idempotencyKey.length <= 128) { "Invalid currency idempotency key" }
        metadata.forEach { (key, value) ->
            require(key.isNotBlank() && key.length <= 64 && value.length <= 512) { "Invalid currency transaction metadata" }
        }
    }
}
