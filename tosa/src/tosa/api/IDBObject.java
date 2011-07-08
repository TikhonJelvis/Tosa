package tosa.api;

import gw.lang.reflect.IType;
import gw.lang.reflect.gs.IGosuObject;

import java.sql.SQLException;
import java.util.Map;

/**
 * IDBObject is the interface implemented by all database objects in Tosa.  It provides basic operations
 * that can be performed on the object, such as getting and setting column values, updating, and deleting
 * the object.
 */
public interface IDBObject extends IGosuObject {

  IDBTable getDBTable();

  Object getColumnValue(String columnName);

  void setColumnValue(String columnName, Object value);

  boolean isNew();

  void update() throws SQLException;

  /**
   * Updates the object, editing the database even if it was created using the constructor rather than one
   * of the methods like fromId.
   */
	void forceUpdate() throws SQLException;

	Map toMap();

  void delete() throws SQLException;
}
