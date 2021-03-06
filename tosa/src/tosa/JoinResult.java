package tosa;

import tosa.api.*;
import org.slf4j.profiler.Profiler;
import tosa.api.IDatabase;
import tosa.loader.DBTypeInfo;
import tosa.loader.Util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: alan
 * Date: 12/29/10
 * Time: 10:36 PM
 * To change this template use File | Settings | File Templates.
 */
public class JoinResult implements List<IDBObject> {

  private List<IDBObject> _result;

  private IDatabase _database;
  private IDBTable _joinTable;
  private IDBColumn _idColumn;
  private IDBColumn _srcColumn;
  private IDBColumn _targetColumn;
  private String _srcId;

  public JoinResult(List<IDBObject> result, IDatabase database, IDBTable joinTable,
                    IDBColumn srcColumn, IDBColumn targetColumn, String srcId) {
    _result = result;
    _database = database;
    _joinTable = joinTable;
    _idColumn = _joinTable.getColumn(DBTypeInfo.ID_COLUMN);
    _srcColumn = srcColumn;
    _targetColumn = targetColumn;
    _srcId = srcId;
  }

  @Override
  public boolean add(IDBObject obj) {
    // TODO - AHK - Determine dynamically if table names should be quoted or not
    Profiler profiler = Util.newProfiler(_srcColumn.getTable().getName() + "." + _joinTable.getName() + ".add()");
    String query = "insert into \"" + _joinTable.getName() + "\" (\"" + _srcColumn.getName() + "\", \"" + _targetColumn.getName() + "\") values (?, ?)";
    try {
      IPreparedStatementParameter[] parameters = new IPreparedStatementParameter[2];
      parameters[0] = _srcColumn.wrapParameterValue(_srcId);
      parameters[1] = _targetColumn.wrapParameterValue(obj.getColumnValue(DBTypeInfo.ID_COLUMN));
      profiler.start(query + "( " + parameters[0] + ", " + parameters[1] + ")");
      _database.getDBExecutionKernel().executeInsert(query, parameters);
    } finally {
      profiler.stop();
    }
    _result.add(obj);
    return true;
  }

  @Override
  public boolean addAll(Collection<? extends IDBObject> objs) {
    List<IPreparedStatementParameter> parameters = new ArrayList<IPreparedStatementParameter>();
    StringBuilder query = new StringBuilder("insert into \"");
    query.append(_joinTable.getName()).append("\" (\"").append(_srcColumn.getName()).append("\", \"").append(_targetColumn.getName()).append("\") values ");
    for (IDBObject obj : objs) {
      parameters.add(_srcColumn.wrapParameterValue(_srcId));
      parameters.add(_targetColumn.wrapParameterValue(obj.getColumnValue(DBTypeInfo.ID_COLUMN)));
      query.append("(?, ?)");
      query.append(", ");
    }
    if (!objs.isEmpty()) {
      query.setLength(query.length() - 2);
    }
    Profiler profiler = Util.newProfiler(_srcColumn.getTable().getName() + "." + _joinTable.getName() + ".addAll()");
    profiler.start(query.toString() + " (" + parameters + ")");
    try {
    _database.getDBExecutionKernel().executeInsert(query.toString(), parameters.toArray(new IPreparedStatementParameter[parameters.size()]));
    } finally {
      profiler.stop();
    }
    _result.addAll(objs);
    return true;
  }

  @Override
  public boolean remove(Object o) {
    Profiler profiler = Util.newProfiler(_srcColumn.getTable().getName() + "." + _joinTable.getName() + ".remove()");
    if (o instanceof CachedDBObject) {
      CachedDBObject obj = (CachedDBObject) o;
      try {
        List<IPreparedStatementParameter> parameters = new ArrayList<IPreparedStatementParameter>();
        parameters.add(_srcColumn.wrapParameterValue(_srcId));
        parameters.add(_targetColumn.wrapParameterValue(obj.getColumns().get(DBTypeInfo.ID_COLUMN)));
        if (_database.getTable(_joinTable.getName()).hasId()) {
          String query = "select * from \"" + _joinTable.getName() + "\" where \"" + _srcColumn.getName() + "\" = ? and \"" + _targetColumn.getName() + "\" = ? limit 1";
          profiler.start(query + " (" + parameters + ")");
          List<Object> results = _database.getDBExecutionKernel().executeSelect(query, new JoinQueryResultProcessor(),
              parameters.toArray(new IPreparedStatementParameter[parameters.size()]));
          if (!results.isEmpty() && results.get(0) != null) {
            parameters.clear();
            parameters.add(_idColumn.wrapParameterValue(results.get(0)));
            query = "delete from \"" + _joinTable.getName() + "\" where \"id\" = ?";
            profiler.start(query + " (" + parameters + ")");
            _database.getDBExecutionKernel().executeDelete(query, parameters.toArray(new IPreparedStatementParameter[parameters.size()]));
            _result.remove(obj);
            return true;
          }
        } else {
          String query = "delete from \"" + _joinTable.getName() + "\" where \"" + _srcColumn.getName() + "\" = ? and \"" + _targetColumn.getName() + "\" = ?";
          profiler.start(query + " (" + parameters + ")");
          _database.getDBExecutionKernel().executeDelete(query, parameters.toArray(new IPreparedStatementParameter[parameters.size()]));
          _result.remove(obj);
          return true;
        }
      } finally {
        profiler.stop();
      }
    }
    return false;
  }

  @Override
  public void clear() {
    Profiler profiler = Util.newProfiler(_srcColumn.getTable().getName() + "." + _joinTable.getName() + ".clear()");
    String query = "delete from \"" + _joinTable.getName() + "\" where \"" + _srcColumn.getName() + "\" = ?";
    IPreparedStatementParameter parameter = _srcColumn.wrapParameterValue(_srcId);
    profiler.start(query + " (" + parameter + ")");
    try {
      _database.getDBExecutionKernel().executeDelete(query, parameter);
    } finally {
      profiler.stop();
    }
    _result.clear();
  }


  @Override
  public int size() {
    return _result.size();
  }

  @Override
  public boolean isEmpty() {
    return _result.isEmpty();
  }

  @Override
  public boolean contains(Object o) {
    return _result.contains(o);
  }

  @Override
  public Iterator<IDBObject> iterator() {
    return _result.iterator();
  }

  @Override
  public Object[] toArray() {
    return _result.toArray();
  }

  @Override
  public <T> T[] toArray(T[] a) {
    return _result.toArray(a);
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return _result.containsAll(c);
  }

  @Override
  public boolean addAll(int index, Collection<? extends IDBObject> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    return _result.retainAll(c);
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof JoinResult) {
      return o == this || _result.equals(((JoinResult)o)._result);
    } else {
      return o instanceof List && _result.equals(o);
    }
  }

  @Override
  public int hashCode() {
    return _result.hashCode();
  }

  @Override
  public IDBObject get(int index) {
    return _result.get(index);
  }

  @Override
  public IDBObject set(int index, IDBObject element) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void add(int index, IDBObject element) {
    throw new UnsupportedOperationException();
  }

  @Override
  public IDBObject remove(int index) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int indexOf(Object o) {
    return _result.indexOf(o);
  }

  @Override
  public int lastIndexOf(Object o) {
    return _result.lastIndexOf(o);
  }

  @Override
  public ListIterator<IDBObject> listIterator() {
    return _result.listIterator();
  }

  @Override
  public ListIterator<IDBObject> listIterator(int index) {
    return _result.listIterator(index);
  }

  @Override
  public List<IDBObject> subList(int fromIndex, int toIndex) {
    return _result.subList(fromIndex, toIndex);
  }

  private static class JoinQueryResultProcessor implements IQueryResultProcessor<Object> {
    @Override
    public Object processResult(ResultSet result) throws SQLException {
      return result.getObject(DBTypeInfo.ID_COLUMN);
    }
  }
}
