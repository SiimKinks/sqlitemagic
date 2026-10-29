package com.siimkinks.sqlitemagic.internal

/**
 * Key of a connection setting that a reactive adapter owns, such as the scheduler of its queries.
 *
 * Keys are compared by identity. Set a value with [ReactiveAdapterSupport.adapterSetting] on a setup builder and read
 * it from an opened connection with [ReactiveAdapterSupport.adapterSetting].
 *
 * @param V Value type
 */
@InternalSqliteMagicApi
class AdapterSettingKey<V : Any>
