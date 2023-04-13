package info.teksol.mindcode.ast;

import java.util.Objects;

public class BreakStatement extends ControlBlockAstNode {
    private final String label;

    BreakStatement(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof BreakStatement statement && Objects.equals(statement.label, label);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(label);
    }

    @Override
    public String toString() {
        return "BreakStatement{" + (label == null ? "" : "label='" + label + '\'') + '}';
    }
}
