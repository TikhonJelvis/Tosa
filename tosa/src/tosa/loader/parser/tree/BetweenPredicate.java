package tosa.loader.parser.tree;

public class BetweenPredicate extends SQLParsedElement{

  private SQLParsedElement _lhs;
  private SQLParsedElement _bottom;
  private SQLParsedElement _top;
  private boolean _not;
  private boolean _symmetric;
  private boolean _asymmetric;

  public BetweenPredicate(SQLParsedElement lhs, SQLParsedElement bottom, SQLParsedElement top, boolean not, boolean symmetric, boolean asymmeteric) {
    super(lhs.firstToken(), top.lastToken(), lhs, bottom, top);
    _lhs = lhs;
    _bottom = bottom;
    _top = top;
    _not = not;
    _symmetric = symmetric;
    _asymmetric = asymmeteric;
  }

  @Override
  protected void toSQL(boolean prettyPrint, int indent, StringBuilder sb) {
    _lhs.toSQL(prettyPrint, indent, sb);
    if(_not) {
      sb.append(" NOT");
    }
    sb.append(" BETWEEN ");
    if (_symmetric) {
      sb.append("SYMMETRIC ");
    } else if (_asymmetric) {
      sb.append("ASYMMETRIC ");
    }
    _bottom.toSQL(prettyPrint, indent, sb);
    sb.append(" AND ");
    _top.toSQL(prettyPrint,indent, sb);
  }

}
