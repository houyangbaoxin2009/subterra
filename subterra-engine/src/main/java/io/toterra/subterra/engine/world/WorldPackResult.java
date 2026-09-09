package io.toterra.subterra.engine.world;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.save.SaveContainer;
import io.toterra.subterra.engine.save.SaveSlot;

import java.util.Map;

/**
 * p.2.9.1 world-pack rehydration result — the typed answer to
 * {@link WorldPack#rehydrate(String)}}: the rehydrated {@link SaveContainer}
 * (six typed slots, mirroring {@code SaveSlot}), the datapack document decoded
 * verbatim from the base64 payload (passed on to
 * {@code DatapackPack.unpack}), and the sorted metadata map.
 * <p>
 * The ledger and domain archives are not re-encoded here: they are reached
 * through {@link #ledger()} / {@link #domain()} straight from the container's
 * {@code SaveSlot.LEDGER} / {@code SaveSlot.DOMAIN} slots, so no duplicate data
 * shape is introduced.
 * <p>
 * p.2.9.1 世界包再水化结果——{@link WorldPack#rehydrate(String)}} 的类型化返回：再水化后的
 * {@link SaveContainer}（六类类型化槽，镜像 {@code SaveSlot}）、从 base64 载荷原样解码出的
 * 数据包文档（转交给 {@code DatapackPack.unpack}）、以及有序中介（meta）表。
 * <p>
 * 藏录/领域档案不在此重复编码：经 {@link #ledger()} / {@link #domain()} 直接从容器的
 * {@code SaveSlot.LEDGER} / {@code SaveSlot.DOMAIN} 槽取用，不引入新的数据形态。
 */
public record WorldPackResult(SaveContainer save, String datapackDocument, Map<String, TdValue> meta) {

    /**
     * 藏录（ledger）档案表；未挂载 → 空表。The ledger archive table; empty when not mounted.
     */
    public TdTable ledger() {
        TdTable t = save.document(SaveSlot.LEDGER);
        return t == null ? TdTable.builder().build() : t;
    }

    /**
     * 领域档案（domain）表；未挂载 → 空表。The domain archive table; empty when not mounted.
     */
    public TdTable domain() {
        TdTable t = save.document(SaveSlot.DOMAIN);
        return t == null ? TdTable.builder().build() : t;
    }
}