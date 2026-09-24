package com.nedko.cineflow.xml.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class WhitespaceAdapterTest {

    private final WhitespaceAdapter adapter = new WhitespaceAdapter();

    @Test
    void unmarshalStripsAndCollapsesWhitespaces() {
        assertEquals("The Movie Title", adapter.unmarshal(" \tThe\n Movie   Title  "));
    }

    @Test
    void unmarshalKeepsNull() {
        assertNull(adapter.unmarshal(null));
    }

    @Test
    void marshalDoesNotAlterTheValue() {
        final String value = "  Preserve   this  ";
        assertSame(value, adapter.marshal(value));
    }
}
