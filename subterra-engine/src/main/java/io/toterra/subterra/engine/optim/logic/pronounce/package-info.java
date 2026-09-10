/**
 * 发音匹配层：将世界内/专有名词按词典做确定性发音匹配。
 * Pronounce-matching layer: deterministic pronunciation matching against a lexicon.
 * <p>
 * 含词典 {@link io.toterra.subterra.engine.optim.logic.pronounce.Lexicon} 与匹配器
 * {@link io.toterra.subterra.engine.optim.logic.pronounce.PronounceMatcher}。纯 JDK，
 * 不碰 MC；匹配顺序确定、可回放。
 * <p>
 * Holds the lexicon {@link io.toterra.subterra.engine.optim.logic.pronounce.Lexicon} and
 * the matcher {@link io.toterra.subterra.engine.optim.logic.pronounce.PronounceMatcher}.
 * Pure JDK, no MC coupling; fixed matching order, replayable.
 */
package io.toterra.subterra.engine.optim.logic.pronounce;