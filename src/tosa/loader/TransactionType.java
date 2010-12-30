package tosa.loader;

import gw.lang.reflect.IType;
import gw.lang.reflect.ITypeInfo;
import gw.lang.reflect.ITypeLoader;
import gw.lang.reflect.TypeBase;
import tosa.DBConnection;

import java.util.Collections;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: alan
 * Date: 12/29/10
 * Time: 10:37 PM
 * To change this template use File | Settings | File Templates.
 */
public class TransactionType extends TypeBase implements ITransactionType {

  private DBConnection _conn;
  private TransactionTypeInfo _typeInfo;
  private DBTypeLoader _typeLoader;

  public TransactionType(DBConnection connInfo, DBTypeLoader dbTypeLoader) {
    _conn = connInfo;
    _typeLoader = dbTypeLoader;
    _typeInfo = new TransactionTypeInfo(this);
  }

  @Override
  public String getName() {
    return _conn.getNamespace() + ".Transaction";
  }

  @Override
  public String getRelativeName() {
    return "Transaction";
  }

  @Override
  public String getNamespace() {
    return _conn.getNamespace();
  }

  @Override
  public ITypeLoader getTypeLoader() {
    return _typeLoader;
  }

  @Override
  public IType getSupertype() {
    return null;
  }

  @Override
  public List<? extends IType> getInterfaces() {
    return Collections.emptyList();
  }

  @Override
  public ITypeInfo getTypeInfo() {
    return _typeInfo;
  }

  public DBConnection getConnection() {
    return _conn;
  }

}
