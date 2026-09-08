package io.toterra.subterra.engine.config;

/**
 * One table entry: a named key/value pair, or a keyless array element
 * (key == null). Package-internal.
 */
record TdEntry(String key, TdValue value) {
}