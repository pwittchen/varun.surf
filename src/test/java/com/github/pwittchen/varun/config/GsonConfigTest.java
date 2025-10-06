package com.github.pwittchen.varun.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GsonConfigTest {

    private final GsonConfig config = new GsonConfig();

    @Test
    void shouldCreateGsonInstance() {
        // when
        var gson = config.gson();

        // then
        assertThat(gson).isNotNull();
    }

    @Test
    void shouldUsePrettyPrinting() {
        // given
        var gson = config.gson();
        TestObject obj = new TestObject("test", "value");

        // when
        var json = gson.toJson(obj);

        // then
        assertThat(json).contains("\n");
        assertThat(json).contains("  ");
    }

    @Test
    void shouldSerializeAndDeserializeCorrectly() {
        // given
        var gson = config.gson();
        TestObject original = new TestObject("field1", "field2");

        // when
        var json = gson.toJson(original);
        var deserialized = gson.fromJson(json, TestObject.class);

        // then
        assertThat(deserialized.nullField()).isEqualTo("field1");
        assertThat(deserialized.stringField()).isEqualTo("field2");
    }

    private record TestObject(String nullField, String stringField) {
    }
}
