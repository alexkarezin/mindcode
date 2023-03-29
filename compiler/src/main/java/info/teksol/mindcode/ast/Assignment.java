package info.teksol.mindcode.ast;

import java.util.Objects;

public class Assignment extends BaseAstNode {
    private final AstNode var;
    private final AstNode value;

    public Assignment(AstNode var, AstNode value) {
        super(var, value);
        this.var = var;
        this.value = value;
    }

    public AstNode getVar() {
        return var;
    }

    public AstNode getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Assignment that = (Assignment) o;
        return Objects.equals(var, that.var) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(var, value);
    }

    @Override
    public String toString() {
        return "Assignment{" +
                "var=" + var +
                ", value=" + value +
                '}';
    }
}
