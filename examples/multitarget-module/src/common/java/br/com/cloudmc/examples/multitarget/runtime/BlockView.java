package br.com.cloudmc.examples.multitarget.runtime;

public final class BlockView {
    public static final BlockView AIR = new BlockView(0, 0, "air");

    private final int id;
    private final int metadata;
    private final String name;

    public BlockView(int id, int metadata, String name) {
        this.id = id;
        this.metadata = metadata;
        this.name = name == null || name.length() == 0 ? "block" : name;
    }

    public int id() {
        return id;
    }

    public int metadata() {
        return metadata;
    }

    public String name() {
        return name;
    }

    public boolean air() {
        return id == 0 || "air".equals(name);
    }

    @Override
    public String toString() {
        return name + "#" + id + ":" + metadata;
    }
}
