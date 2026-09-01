package com.basper.peakpvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

final class Messages {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private Messages() {}
    static Component legacy(String value) { return LEGACY.deserialize(value == null ? "" : value); }
}
