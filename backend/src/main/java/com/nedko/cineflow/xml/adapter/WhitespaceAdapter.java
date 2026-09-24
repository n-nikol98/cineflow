package com.nedko.cineflow.xml.adapter;

import java.util.regex.Pattern;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

/**
 * JAXB adapter that normalizes whitespaces in unmarshalled XML text content: leading and
 * trailing whitespaces are stripped, and any internal run of whitespaces (including
 * newlines/tabs introduced by pretty-printed XML) is collapsed to a single space.
 * <p>
 * Marshalling is a no-op passthrough, since this adapter only exists to clean up
 * incoming XML, not to reformat outgoing values.
 */
public class WhitespaceAdapter extends XmlAdapter<String, String> {

    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    /**
     * Strips leading/trailing whitespace from {@code value} and collapses any internal
     * run of whitespaces to a single space. Returns {@code null} unchanged.
     *
     * @param value the raw XML text content, or {@code null}
     * @return the whitespace-normalized value, or {@code null} if {@code value} was
     *         {@code null}
     */
    @Override
    public String unmarshal(final String value) {
        return value == null ? null : WHITESPACE_RUN
            .matcher(value.strip()).replaceAll(" ");
    }

    /**
     * Returns {@code value} unchanged; no normalization is needed when marshalling.
     *
     * @param value the value to marshal
     * @return the unchanged {@code value}
     */
    @Override
    public String marshal(final String value) {
        return value;
    }
}
