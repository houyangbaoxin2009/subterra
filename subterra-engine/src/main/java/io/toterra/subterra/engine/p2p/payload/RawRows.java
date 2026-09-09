package io.toterra.subterra.engine.p2p.payload;

import io.toterra.subterra.engine.zd.ZdRow;

import java.util.List;

/**
 * p.2.5.5 载荷载体小记录：把「行 + 通道元数据」圈成一个值对象，避免裸传 {@code List<ZdRow>}。
 * 不可变的防御性拷贝；两者皆为可空容忍（null → 空）。仅作为 codec 入参/出参的薄胶囊，不持有任何
 * 传输或会话状态。
 * <p>
 * p.2.5.5 minimal payload carrier: bundles "rows + channel metadata" into one value object instead
 * of passing a bare {@code List<ZdRow>}. Immutable defensive copies; both fields are null-tolerant
 * (null → empty). A thin capsule for codec input/output only — it holds no transport or session state.
 *
 * @param rows        zd 行载荷 / the zd rows payload
 * @param channelMeta 不透明通道元数据（如目标 NodeId 十六进制、投递路由等）/ opaque channel metadata
 *                    (e.g. target NodeId hex, delivery routing), possibly empty
 */
public record RawRows(List<ZdRow> rows, byte[] channelMeta) {

    public RawRows {
        rows = rows == null ? List.of() : List.copyOf(rows);
        channelMeta = channelMeta == null ? new byte[0] : channelMeta.clone();
    }

    /** 行列表的防御性副本。Defensive copy of the row list. */
    @Override
    public List<ZdRow> rows() {
        return rows;
    }

    /** 通道元数据的防御性副本。Defensive copy of the channel metadata. */
    @Override
    public byte[] channelMeta() {
        return channelMeta.clone();
    }
}