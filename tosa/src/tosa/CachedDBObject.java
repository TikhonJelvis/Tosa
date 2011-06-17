package tosa;

import gw.lang.reflect.TypeSystem;
import gw.lang.reflect.gs.IGosuObject;
import org.slf4j.profiler.Profiler;
import tosa.api.*;
import tosa.loader.DBTypeInfo;
import tosa.loader.IDBType;
import tosa.loader.Util;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: alan
 * Date: 12/29/10
 * Time: 10:32 PM
 * To change this template use File | Settings | File Templates.
 */
public class CachedDBObject implements IDBObject {
  private Map<String, Object> _columns;
  private Map<String, Object> _cachedValues;
  private IDBType _type;
  private boolean _new;

  @Override
  public IDBType getIntrinsicType() {
    return _type;
  }

  @Override
  public IDBTable getDBTable() {
    return _type.getTable();
  }

  private String getTableName() {
    return getDBTable().getName();
  }

  @Override
  public boolean isNew() {
    return _new;
  }

  @Override
  public Object getColumnValue(String columnName) {
    // TODO - AHK - Validate that the column name is actually a legal column
    return _columns.get(columnName);
  }

  @Override
  public void setColumnValue(String columnName, Object value) {
    _columns.put(columnName, value);
  }

	public Map<String, Object> getColumns() {
    return _columns;
  }

  public Map<String, Object> getCachedValues() {
    return _cachedValues;
  }

  public CachedDBObject(IDBType type, boolean isNew) {
    // TODO - AHK
    _type = (IDBType) TypeSystem.getOrCreateTypeReference(type);
    _new = isNew;
    _columns = new HashMap<String, Object>();
    _cachedValues = new HashMap<String, Object>();
  }

	@Override
	public void forceUpdate() throws SQLException {
		_new = false;
		update();
	}

  @Override
  public void update() throws SQLException {
    Profiler profiler = Util.newProfiler(_type.getName() + ".update()");
    IDatabase database = _type.getTable().getDatabase();
    List<String> attrs = new ArrayList<String>();
    List<IPreparedStatementParameter> values = new ArrayList<IPreparedStatementParameter>();
    for (Map.Entry<String, Object> entry : _columns.entrySet()) {
      if (entry.getKey().equals(DBTypeInfo.ID_COLUMN)) {
        continue;
      }
      IDBColumn column = _type.getTable().getColumn(entry.getKey());
      if (column != null) {
        attrs.add("\"" + entry.getKey() + "\"");
        Object value = entry.getValue();
        values.add(column.wrapParameterValue(value));
      }
    }
    try {
      if (_new) {
        StringBuilder query = new StringBuilder("insert into \"");
        query.append(getTableName()).append("\" (");
        for (String key : attrs) {
          query.append(key);
          if (key != attrs.get(attrs.size() - 1)) {
            query.append(", ");
          }
        }
        query.append(") values (");
        for (int i = 0; i < attrs.size(); i++) {
          if (i > 0) {
            query.append(", ");
          }
          query.append("?");
        }
        query.append(")");
        profiler.start(query.toString() + " (" + values + ")");
        Object id = database.getDBExecutionKernel().executeInsert(query.toString(), values.toArray(new IPreparedStatementParameter[values.size()]));
        if (id != null) {
          _columns.put(DBTypeInfo.ID_COLUMN, id);
          _new = false;
        }
      } else {
        StringBuilder query = new StringBuilder("update \"");
        query.append(getTableName()).append("\" set ");
        for (String attr : attrs) {
          query.append(attr).append(" = ?");
          if (attr != attrs.get(attrs.size() - 1)) {
            query.append(", ");
          }
        }
        query.append(" where \"id\" = ?");
        values.add(_type.getTable().getColumn(DBTypeInfo.ID_COLUMN).wrapParameterValue(_columns.get(DBTypeInfo.ID_COLUMN)));
        profiler.start(query.toString() + " (" + values + ")");
        database.getDBExecutionKernel().executeUpdate(query.toString(), values.toArray(new IPreparedStatementParameter[values.size()]));
      }
    } finally {
      profiler.stop();
    }
  }

  @Override
  public void delete() throws SQLException {
    // TODO - AHK - Determine if we need to quote the table name or column names or not
    String query = "delete from \"" + getTableName() + "\" where \"id\" = ?";
    IDatabase database = _type.getTable().getDatabase();
    IPreparedStatementParameter parameter = _type.getTable().getColumn(DBTypeInfo.ID_COLUMN).wrapParameterValue(_columns.get(DBTypeInfo.ID_COLUMN));
    Profiler profiler = Util.newProfiler(_type.getName() + ".delete()");
    profiler.start(query + " (" + parameter + ")");
    try {
      database.getDBExecutionKernel().executeDelete(query, parameter);
    } finally {
      profiler.stop();
    }
  }

  @Override
  public String toString() {
    return _columns.toString();
  }

  @Override
  public int hashCode() {
    int hashCode = _type.hashCode();
    List<String> keys = new ArrayList<String>(_columns.keySet());
    Collections.sort(keys);
    for (String columnName : keys) {
      if (_columns.get(columnName) != null) {
        hashCode = hashCode * 17 + _columns.get(columnName).hashCode();
      } else {
        hashCode *= 17;
      }
    }
    return hashCode;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof CachedDBObject) {
      CachedDBObject other = (CachedDBObject) obj;
      if (_type.equals(other._type)) {
        for (String columnName : _columns.keySet()) {
          if (_columns.get(columnName) != null) {
            if (!_columns.get(columnName).equals(other._columns.get(columnName))) {
              return false;
            }
          }
        }
        for (String columnName : other._columns.keySet()) {
          if (other._columns.get(columnName) != null) {
            if (!other._columns.get(columnName).equals(_columns.get(columnName))) {
              return false;
            }
          }
        }
        return true;
      }
    }
    return false;
  }

}
