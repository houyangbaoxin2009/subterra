package io.toterra.subterra.engine.datapack;

/**
 * p.2.2 tie-logic library declaration from the optional {@code pack.td}
 * manifest: {@code [ lib = <tie namespace>, dll = <path> ]}. {@code dll} is
 * resolved relative to the pack directory when not absolute. The lib name is
 * the tie {@code namespace} of the compiled library, so a library exported as
 * {@code namespace::fn} binds to the FFM symbol {@code namespace$fn}.
 */
public record TieLibDecl(String lib, String dll) {
}