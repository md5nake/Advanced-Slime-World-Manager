package com.infernalsuite.aswm.nms.format.impl.blob;

import com.infernalsuite.aswm.nms.format.impl.AbstractWorldFormat;

import java.util.HashMap;
import java.util.Map;

/*
 * The idea here is to be able to support not loading
 * the entire world into memory, and instead being able to retrieve chunks when
 * needed by having some sort of "map" to reference the chunks in the byte blob.
 * Basically, the region file format...
 *
 * The issue however is that this prevents the format from being able to be deflated, since
 * you will have to rebuild the header if you want to do that.
 * So, it might be worth investigating if there is some sort of format that can achieve what we want.
 *
 * mock format:
 * int (length)
 * long (chunk-key) int (offset)
 * long (chunk-key) int (offset)
 * long (chunk-key) int (offset)
 * ---
 * BLOB:
 * <chunk blob>
 * <chunk blob>
 * <chunk blob>
 */
public class ByteBlobFormat extends AbstractWorldFormat {

    @Override
    public byte[] serialize() {
        return new byte[0];
    }

    private byte[] serializeHeader() {
        return new byte[0];
    }
}
