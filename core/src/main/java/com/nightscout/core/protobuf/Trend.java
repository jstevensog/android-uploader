// Code generated by Wire protocol buffer compiler, do not edit.
// Source file: /Users/klee/Projects/Nightscout/android-uploader/core/src/main/java/com/nightscout/core/protobuf/G4Download.proto
package com.nightscout.core.protobuf;

import com.squareup.wire.ProtoEnum;

public enum Trend
        implements ProtoEnum {
    TREND_NONE(0),
    DOUBLE_UP(1),
    SINGLE_UP(2),
    FORTY_FIVE_UP(3),
    FLAT(4),
    FORTY_FIVE_DOWN(5),
    SINGLE_DOWN(6),
    DOUBLE_DOWN(7),
    NOT_COMPUTABLE(8),
    RATE_OUT_OF_RANGE(9);

    private final int value;

    private Trend(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }
}
