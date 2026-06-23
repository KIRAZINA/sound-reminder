package com.soundreminder.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public class Tag {

    private String name;
    private String colorHex;

    public Tag() {
        this.name = "";
        this.colorHex = "#0078D4";
    }

    @JsonCreator
    public Tag(
            @JsonProperty("name") String name,
            @JsonProperty("colorHex") String colorHex) {
        this.name = name;
        this.colorHex = (colorHex != null && colorHex.matches("^#[0-9A-Fa-f]{6}$"))
                ? colorHex : "#0078D4";
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) {
        this.colorHex = (colorHex != null && colorHex.matches("^#[0-9A-Fa-f]{6}$"))
                ? colorHex : "#0078D4";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tag tag = (Tag) o;
        return Objects.equals(name, tag.name);
    }

    @Override
    public int hashCode() { return Objects.hash(name); }

    @Override
    public String toString() { return "Tag{name='" + name + "', color=" + colorHex + "}"; }
}
